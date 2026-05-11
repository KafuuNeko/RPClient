package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class OpenAICompatibleLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mProvider: LLMProviderConfig
) : LLMClient {
    /**
     * OpenAI-compatible 非流式调用，适用于 ChatGPT、DeepSeek、OpenRouter 等服务。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = runCatching {
            mOkHttpClient.await(httpRequest.request)
        }.onSuccess {
            logRequest(model, false, httpRequest.payloadJson, it)
        }.onFailure {
            logRequest(model, false, httpRequest.payloadJson, it.toErrorJson())
        }.getOrThrow()
        return raw.toOpenAIResponse(
            fallbackModel = model,
            includeReasoningInContent = request.includeReasoningInContent
        )
    }

    /**
     * OpenAI-compatible 流式调用，解析 chat.completion.chunk 的 delta.content。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return flow {
            var isThinking = false
            val httpRequest = buildRequest(request, model, stream = true)
            val rawChunks = JSONArray()
            runCatching {
                mOkHttpClient.streamLines(httpRequest.request).collect { line ->
                    rawChunks.put(line)
                    val event = line.toOpenAIStreamEvent(
                        includeReasoningInContent = request.includeReasoningInContent,
                        isThinking = isThinking,
                        onThinkingStateChange = { isThinking = it }
                    ) ?: return@collect
                    emit(event)
                }
                if (request.includeReasoningInContent && isThinking) {
                    emit(LLMStreamEvent.Delta(content = "\n</think>\n\n", rawChunk = "reasoning_close"))
                }
            }.onSuccess {
                logRequest(model, true, httpRequest.payloadJson, rawChunks.toString())
            }.onFailure {
                logRequest(model, true, httpRequest.payloadJson, it.toErrorJson())
                throw it
            }
        }
    }

    /**
     * 构建 OpenAI-compatible 请求体。stream 参数决定接口返回完整响应还是 SSE 增量。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): LLMHttpRequest {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", request.messages.toOpenAIMessages())
            .put("top_p", request.options.topP ?: mProvider.topP)
            .put("temperature", request.options.temperature ?: mProvider.temperature)
            .put("max_tokens", request.options.maxTokens ?: mProvider.maxTokens)
            .put("stream", stream)
        if (request.options.stop.isNotEmpty()) payload.put("stop", request.options.stop.toJsonArray())

        return LLMHttpRequest(
            request = Request.Builder()
            .url("${mProvider.normalizedBaseUrl()}/chat/completions")
            .post(payload.toRequestBody())
            .header("Authorization", "Bearer ${mProvider.apiKey}")
            .header("Content-Type", "application/json")
            .applyProviderHeaders(mProvider)
            .build(),
            payloadJson = payload.toString()
        )
    }

    private suspend fun logRequest(
        model: String,
        isStreaming: Boolean,
        requestJson: String,
        responseJson: String
    ) {
        runCatching {
            mLLMRequestLogRepository.saveLog(
                provider = mProvider,
                model = model,
                isStreaming = isStreaming,
                requestJson = requestJson,
                responseJson = responseJson
            )
        }
    }

    /**
     * 转换通用消息为 OpenAI-compatible messages 数组。
     */
    private fun List<LLMMessage>.toOpenAIMessages(): JSONArray {
        return JSONArray().also { array ->
            forEach { message ->
                array.put(
                    JSONObject()
                        .put("role", message.toOpenAIRole())
                        .put("content", message.content)
                )
            }
        }
    }

    /**
     * 解析非流式完整响应。
     */
    private fun String.toOpenAIResponse(
        fallbackModel: String,
        includeReasoningInContent: Boolean
    ): LLMGenerationResponse {
        val json = JSONObject(this)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val message = choice.optJSONObject("message")
        val usageJson = json.optJSONObject("usage")
        val reasoningContent = message?.optReasoningContent().orEmpty()
        val content = message?.optCleanString("content").orEmpty()
        return LLMGenerationResponse(
            content = mergeReasoningContent(
                reasoningContent = reasoningContent,
                content = content,
                includeReasoningInContent = includeReasoningInContent
            ),
            model = json.optString("model", fallbackModel),
            provider = mProvider.providerType,
            usage = usageJson?.let {
                LLMUsage(
                    promptTokens = it.optNullableInt("prompt_tokens"),
                    completionTokens = it.optNullableInt("completion_tokens"),
                    totalTokens = it.optNullableInt("total_tokens")
                )
            },
            rawResponse = this
        )
    }

    /**
     * 解析 OpenAI-compatible SSE 行。
     */
    private fun String.toOpenAIStreamEvent(
        includeReasoningInContent: Boolean,
        isThinking: Boolean,
        onThinkingStateChange: (Boolean) -> Unit
    ): LLMStreamEvent? {
        if (!startsWith("data:")) return null
        val data = removePrefix("data:").trim()
        if (data == "[DONE]") return LLMStreamEvent.Finished(rawChunk = this)
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val deltaObject = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?: return null
        val reasoningContent = deltaObject.optReasoningContent()
        if (reasoningContent.isNotBlank()) {
            if (!includeReasoningInContent) return null
            val content = if (isThinking) reasoningContent else "<think>\n$reasoningContent"
            onThinkingStateChange(true)
            return LLMStreamEvent.Delta(content = content, rawChunk = data)
        }
        val content = deltaObject.optCleanString("content").orEmpty()
        if (content.isBlank()) return null
        val mergedContent = if (includeReasoningInContent && isThinking) "\n</think>\n\n$content" else content
        onThinkingStateChange(false)
        return LLMStreamEvent.Delta(content = mergedContent, rawChunk = data)
    }

    private fun JSONObject.optReasoningContent(): String {
        return optCleanString("reasoning_content")
            .ifBlank { optCleanString("reasoning") }
            .ifBlank { optCleanString("reasoningContent") }
    }

    private fun JSONObject.optCleanString(name: String): String {
        if (!has(name) || isNull(name)) return ""
        val value = optString(name).trim()
        return if (value.equals("null", ignoreCase = true)) "" else value
    }

    private fun mergeReasoningContent(
        reasoningContent: String,
        content: String,
        includeReasoningInContent: Boolean
    ): String {
        if (!includeReasoningInContent || reasoningContent.isBlank()) return content
        return "<think>\n$reasoningContent\n</think>\n\n$content".trim()
    }

    /**
     * 读取可空整数字段。
     */
    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }
}
