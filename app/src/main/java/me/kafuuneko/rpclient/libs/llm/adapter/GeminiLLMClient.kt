package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.llm.model.resolveFor
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gemini generateContent/streamGenerateContent 协议适配器。
 *
 * 通用消息会转换为 Gemini 的 user/model 角色；开头连续 system 消息通过
 * systemInstruction 发送，历史中的 system 消息以显式标签降级保存。
 */
class GeminiLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mProvider: LLMProviderConfig
) : LLMClient {
    /**
     * Gemini 非流式调用，等待 generateContent 返回完整文本。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = runCatching {
            mOkHttpClient.await(httpRequest.request)
        }.onSuccess {
            mLLMRequestLogRepository.trySaveLog(
                mProvider,
                model,
                false,
                httpRequest.payloadJson,
                it
            )
        }.onFailure {
            mLLMRequestLogRepository.trySaveLog(
                mProvider,
                model,
                false,
                httpRequest.payloadJson,
                it.toErrorJson()
            )
        }.getOrThrow()
        return raw.toGeminiResponse(
            fallbackModel = model,
            includeReasoningInContent = request.includeReasoningInContent
        )
    }

    /**
     * Gemini 流式调用，解析 streamGenerateContent 的 SSE 文本片段。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return flow {
            val httpRequest = buildRequest(request, model, stream = true)
            val rawChunks = JSONArray()
            val partMapper = LLMStreamPartMapper(
                includeReasoningInContent = request.includeReasoningInContent,
                captureReasoning = request.captureReasoning
            )
            runCatching {
                mOkHttpClient.streamLines(
                    request = httpRequest.request,
                    onConnected = { emit(LLMStreamEvent.Connected) }
                ).collect { line ->
                    rawChunks.put(line)
                    parseGeminiStreamParts(line).forEach { part ->
                        partMapper.map(part).forEach { emit(it) }
                    }
                }
                // 兼容未发送 finishReason 便关闭连接的代理服务
                partMapper.finish().forEach { emit(it) }
            }.onSuccess {
                mLLMRequestLogRepository.trySaveLog(
                    mProvider,
                    model,
                    true,
                    httpRequest.payloadJson,
                    rawChunks.toString()
                )
            }.onFailure {
                mLLMRequestLogRepository.trySaveLog(
                    mProvider,
                    model,
                    true,
                    httpRequest.payloadJson,
                    it.toErrorJson()
                )
                throw it
            }
        }
    }

    /**
     * 构建 Gemini 请求。stream=true 时切换到 streamGenerateContent 并启用 SSE。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): LLMHttpRequest {
        val options = request.options.resolveFor(mProvider)
        val generationConfig = JSONObject()
            .put("maxOutputTokens", options.maxTokens)
        if (request.captureReasoning) {
            generationConfig.put(
                "thinkingConfig",
                JSONObject().put("includeThoughts", true)
            )
        }
        options.temperature?.let { generationConfig.put("temperature", it) }
        options.topP?.let { generationConfig.put("topP", it) }
        if (options.stop.isNotEmpty()) {
            generationConfig.put("stopSequences", options.stop.toJsonArray())
        }
        val payload = JSONObject()
            .put("contents", request.messages.toGeminiContents())
            .put("generationConfig", generationConfig)
        val systemInstruction = request.messages.leadingSystemPrompt()
        if (!systemInstruction.isNullOrBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
        }
        val finalPayload = payload.withRequestBodyExtensions(mProvider, request)

        val action = if (stream) "streamGenerateContent" else "generateContent"
        val url = "${mProvider.normalizedBaseUrl()}/v1beta/models/$model:$action"
            .toHttpUrl()
            .newBuilder()
            .apply { if (mProvider.apiKey.isNotBlank()) addQueryParameter("key", mProvider.apiKey) }
            .apply { if (stream) addQueryParameter("alt", "sse") }
            .build()
        return LLMHttpRequest(
            request = Request.Builder()
                .url(url)
                .post(finalPayload.toRequestBody())
                .header("Content-Type", "application/json")
                .applyProviderHeaders(mProvider)
                .build(),
            payloadJson = finalPayload.toString()
        )
    }

    /**
     * 转换通用消息为 Gemini contents 数组。
     */
    private fun List<LLMMessage>.toGeminiContents(): JSONArray {
        return JSONArray().also { array ->
            toAlternatingConversationMessages().forEach { message ->
                array.put(
                    JSONObject()
                        .put("role", message.toGeminiRole())
                        .put("parts", JSONArray().put(JSONObject().put("text", message.content)))
                )
            }
        }
    }

    /**
     * 解析 Gemini 非流式完整响应。
     */
    private fun String.toGeminiResponse(
        fallbackModel: String,
        includeReasoningInContent: Boolean
    ): LLMGenerationResponse {
        val json = JSONObject(this)
        val candidates = json.optJSONArray("candidates")
        val parts = candidates
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
        val content = parts?.joinGeminiTextParts(thought = false).orEmpty()
        val reasoningContent = parts?.joinGeminiTextParts(thought = true).orEmpty()
        return LLMGenerationResponse(
            content = if (includeReasoningInContent) {
                mergeReasoningContent(reasoningContent, content)
            } else {
                content
            },
            model = fallbackModel,
            provider = mProvider.providerType,
            usage = parseGeminiUsage(this),
            reasoningContent = reasoningContent,
            finishReason = candidates
                ?.optJSONObject(0)
                ?.optString("finishReason")
                ?.takeIf { it.isNotBlank() },
            rawResponse = this
        )
    }

    private fun JSONArray.joinGeminiTextParts(thought: Boolean): String {
        return buildString {
            for (index in 0 until length()) {
                val part = optJSONObject(index) ?: continue
                if (part.optBoolean("thought") == thought) append(part.optString("text"))
            }
        }
    }

}

/** 解析 Gemini SSE 行，并按 thought 标记分离构思摘要与最终文本。 */
internal fun parseGeminiStreamParts(line: String): List<LLMProviderStreamPart> {
    if (!line.startsWith("data:")) return emptyList()
    val data = line.removePrefix("data:").trim()
    val json = parseStreamJsonObject(data) ?: return emptyList()
    val candidate = json.arrayOrNull("candidates")
        ?.firstOrNull()
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
    val parts = candidate?.objectOrNull("content")?.arrayOrNull("parts")
    val usage = json.geminiUsage()
    return buildList {
        usage?.let { add(LLMProviderStreamPart.Usage(it)) }
        // Gemini 通过 thought 标记区分思考摘要与最终文本，顺序必须原样保留
        if (parts != null) {
            for (element in parts) {
                val part = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val text = part.cleanString("text").takeIf { it.isNotBlank() } ?: continue
                if (part.booleanOrFalse("thought")) {
                    add(LLMProviderStreamPart.Reasoning(text, data))
                } else {
                    add(LLMProviderStreamPart.Text(text, data))
                }
            }
        }
        candidate?.cleanString("finishReason")?.takeIf { it.isNotBlank() }?.let {
            add(
                LLMProviderStreamPart.Finished(
                    rawChunk = data,
                    finishReason = it,
                    terminal = false
                )
            )
        }
    }
}

/** 解析 Gemini usageMetadata，并将思考与候选输出统一计入输出 Token。 */
internal fun parseGeminiUsage(value: String): LLMUsage? {
    return parseStreamJsonObject(value)?.geminiUsage()
}

private fun JsonObject.geminiUsage(): LLMUsage? {
    val usage = objectOrNull("usageMetadata") ?: return null
    val promptTokens = usage.intOrNull("promptTokenCount")
    val candidateTokens = usage.intOrNull("candidatesTokenCount")
    val thoughtTokens = usage.intOrNull("thoughtsTokenCount")
    val totalTokens = usage.intOrNull("totalTokenCount")
    // Gemini 总量包含候选正文与思考；优先用总量差值兼容未来新增的输出类别
    val completionTokens = if (totalTokens != null && promptTokens != null) {
        (totalTokens - promptTokens).coerceAtLeast(0)
    } else if (candidateTokens != null || thoughtTokens != null) {
        (candidateTokens ?: 0) + (thoughtTokens ?: 0)
    } else {
        null
    }
    return LLMUsage(
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        totalTokens = totalTokens,
        cachedPromptTokens = usage.intOrNull("cachedContentTokenCount"),
        reasoningTokens = thoughtTokens
    )
}
