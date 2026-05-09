package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GeminiLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProvider
) : LLMClient {
    /**
     * Gemini 非流式调用，等待 generateContent 返回完整文本。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = mOkHttpClient.await(httpRequest)
        return raw.toGeminiResponse(model)
    }

    /**
     * Gemini 流式调用，解析 streamGenerateContent 的 SSE 文本片段。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return mOkHttpClient.streamLines(buildRequest(request, model, stream = true))
            .mapNotNull { line -> line.toGeminiStreamEvent() }
    }

    /**
     * 构建 Gemini 请求。stream=true 时切换到 streamGenerateContent 并启用 SSE。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): Request {
        val payload = JSONObject()
            .put("contents", request.messages.toGeminiContents())
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", request.options.temperature ?: mProvider.temperature)
                    .put("maxOutputTokens", request.options.maxTokens ?: mProvider.maxTokens)
                    .also { config ->
                        config.putIfNotNull("topP", request.options.topP)
                        if (request.options.stop.isNotEmpty()) {
                            config.put("stopSequences", request.options.stop.toJsonArray())
                        }
                    }
            )
        val systemInstruction = request.messages
            .firstOrNull { it.role == LLMMessageRole.System }
            ?.content
        if (!systemInstruction.isNullOrBlank()) {
            payload.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
        }

        val action = if (stream) "streamGenerateContent" else "generateContent"
        val url = "${mProvider.normalizedBaseUrl()}/v1beta/models/$model:$action"
            .toHttpUrl()
            .newBuilder()
            .apply { if (mProvider.apiKey.isNotBlank()) addQueryParameter("key", mProvider.apiKey) }
            .apply { if (stream) addQueryParameter("alt", "sse") }
            .build()
        return Request.Builder()
            .url(url)
            .post(payload.toRequestBody())
            .header("Content-Type", "application/json")
            .applyProviderHeaders(mProvider)
            .build()
    }

    /**
     * 转换通用消息为 Gemini contents 数组。
     */
    private fun List<LLMMessage>.toGeminiContents(): JSONArray {
        return JSONArray().also { array ->
            filter { it.role != LLMMessageRole.System }.forEach { message ->
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
    private fun String.toGeminiResponse(fallbackModel: String): LLMGenerationResponse {
        val json = JSONObject(this)
        val candidates = json.optJSONArray("candidates")
        val content = candidates
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.joinText()
            .orEmpty()
        return LLMGenerationResponse(
            content = content,
            model = fallbackModel,
            provider = mProvider.providerType,
            rawResponse = this
        )
    }

    /**
     * 解析 Gemini SSE 行。
     */
    private fun String.toGeminiStreamEvent(): LLMStreamEvent? {
        if (!startsWith("data:")) return null
        val data = removePrefix("data:").trim()
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val text = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.joinText()
            .orEmpty()
        if (text.isBlank()) return null
        return LLMStreamEvent.Delta(content = text, rawChunk = data)
    }

    /**
     * 拼接 Gemini parts 中的 text 字段。
     */
    private fun JSONArray.joinText(): String {
        return buildString {
            for (index in 0 until length()) {
                append(optJSONObject(index)?.optString("text").orEmpty())
            }
        }
    }
}
