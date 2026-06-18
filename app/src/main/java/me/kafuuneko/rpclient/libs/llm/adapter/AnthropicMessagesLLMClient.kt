package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.llm.model.resolveFor
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic Messages API 适配器。
 *
 * Anthropic 仅允许独立的前置 system 字段，因此开头连续 system 消息会被提取，
 * 历史中的 system 消息则以带标签的 user 内容保留其语义。
 */
class AnthropicMessagesLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mProvider: LLMProviderConfig
) : LLMClient {
    /**
     * Anthropic Messages 非流式调用，适用于 Claude 官方接口。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = runCatching {
            mOkHttpClient.await(httpRequest.request)
        }.onSuccess {
            mLLMRequestLogRepository.trySaveLog(mProvider, model, false, httpRequest.payloadJson, it)
        }.onFailure {
            mLLMRequestLogRepository.trySaveLog(mProvider, model, false, httpRequest.payloadJson, it.toErrorJson())
        }.getOrThrow()
        return raw.toAnthropicResponse(model)
    }

    /**
     * Anthropic Messages 流式调用，解析 content_block_delta 事件中的 text。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return flow {
            val httpRequest = buildRequest(request, model, stream = true)
            val rawChunks = JSONArray()
            runCatching {
                mOkHttpClient.streamLines(httpRequest.request).collect { line ->
                    rawChunks.put(line)
                    emit(line.toAnthropicStreamEvent() ?: return@collect)
                }
            }.onSuccess {
                mLLMRequestLogRepository.trySaveLog(mProvider, model, true, httpRequest.payloadJson, rawChunks.toString())
            }.onFailure {
                mLLMRequestLogRepository.trySaveLog(mProvider, model, true, httpRequest.payloadJson, it.toErrorJson())
                throw it
            }
        }
    }

    /**
     * 构建 Anthropic Messages 请求体。stream 参数控制是否返回 SSE。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): LLMHttpRequest {
        val options = request.options.resolveFor(mProvider)
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", options.maxTokens)
            .put("messages", request.messages.toAnthropicMessages())
            .put("stream", stream)
        options.temperature?.let { payload.put("temperature", it) }
        options.topP?.let { payload.put("top_p", it) }
        val systemPrompt = request.messages.leadingSystemPrompt()
        if (systemPrompt.isNotBlank()) payload.put("system", systemPrompt)
        if (options.stop.isNotEmpty()) payload.put("stop_sequences", options.stop.toJsonArray())

        return LLMHttpRequest(
            request = Request.Builder()
                .url("${mProvider.normalizedBaseUrl()}/v1/messages")
                .post(payload.toRequestBody())
                .header("x-api-key", mProvider.apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .applyProviderHeaders(mProvider)
                .build(),
            payloadJson = payload.toString()
        )
    }

    /**
     * 转换通用消息为 Anthropic messages 数组。
     */
    private fun List<LLMMessage>.toAnthropicMessages(): JSONArray {
        return JSONArray().also { array ->
            toAlternatingConversationMessages().forEach { message ->
                array.put(
                    JSONObject()
                        .put("role", message.toAnthropicRole())
                        .put("content", message.content)
                )
            }
        }
    }

    /**
     * 解析 Anthropic 非流式完整响应。
     */
    private fun String.toAnthropicResponse(fallbackModel: String): LLMGenerationResponse {
        val json = JSONObject(this)
        return LLMGenerationResponse(
            content = json.optJSONArray("content")?.joinTextFields(type = "text").orEmpty(),
            model = json.optString("model", fallbackModel),
            provider = mProvider.providerType,
            usage = json.optJSONObject("usage")?.toAnthropicUsage(),
            finishReason = json.optString("stop_reason").takeIf { it.isNotBlank() },
            rawResponse = this
        )
    }

    private fun JSONObject.toAnthropicUsage(): LLMUsage {
        val inputTokens = optNullableInt("input_tokens")
        val outputTokens = optNullableInt("output_tokens")
        return LLMUsage(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            totalTokens = listOfNotNull(inputTokens, outputTokens)
                .takeIf { it.isNotEmpty() }
                ?.sum(),
            promptCacheReadTokens = optNullableInt("cache_read_input_tokens"),
            promptCacheWriteTokens = optNullableInt("cache_creation_input_tokens")
        )
    }

    /**
     * 解析 Anthropic SSE 行。非 data 行会被忽略。
     */
    private fun String.toAnthropicStreamEvent(): LLMStreamEvent? {
        if (!startsWith("data:")) return null
        val data = removePrefix("data:").trim()
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        if (json.optString("type") == "message_stop") {
            return LLMStreamEvent.Finished(rawChunk = data)
        }
        val delta = json.optJSONObject("delta") ?: return null
        delta.optString("stop_reason").takeIf { it.isNotBlank() }?.let {
            return LLMStreamEvent.Finished(rawChunk = data, finishReason = it)
        }
        val text = delta.optString("text")
        if (text.isBlank()) return null
        return LLMStreamEvent.Delta(content = text, rawChunk = data)
    }

}
