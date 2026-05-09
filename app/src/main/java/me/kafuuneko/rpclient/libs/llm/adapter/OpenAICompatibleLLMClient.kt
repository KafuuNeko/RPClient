package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class OpenAICompatibleLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProvider
) : LLMClient {
    /**
     * OpenAI-compatible 非流式调用，适用于 ChatGPT、DeepSeek、OpenRouter 等服务。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = mOkHttpClient.await(httpRequest)
        return raw.toOpenAIResponse(model)
    }

    /**
     * OpenAI-compatible 流式调用，解析 chat.completion.chunk 的 delta.content。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return mOkHttpClient.streamLines(buildRequest(request, model, stream = true))
            .mapNotNull { line -> line.toOpenAIStreamEvent() }
    }

    /**
     * 构建 OpenAI-compatible 请求体。stream 参数决定接口返回完整响应还是 SSE 增量。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): Request {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", request.messages.toOpenAIMessages())
            .put("top_p", request.options.topP ?: mProvider.topP)
            .put("temperature", request.options.temperature ?: mProvider.temperature)
            .put("max_tokens", request.options.maxTokens ?: mProvider.maxTokens)
            .put("stream", stream)
        if (request.options.stop.isNotEmpty()) payload.put("stop", request.options.stop.toJsonArray())

        return Request.Builder()
            .url("${mProvider.normalizedBaseUrl()}/chat/completions")
            .post(payload.toRequestBody())
            .header("Authorization", "Bearer ${mProvider.apiKey}")
            .header("Content-Type", "application/json")
            .applyProviderHeaders(mProvider)
            .build()
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
    private fun String.toOpenAIResponse(fallbackModel: String): LLMGenerationResponse {
        val json = JSONObject(this)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val message = choice.optJSONObject("message")
        val usageJson = json.optJSONObject("usage")
        return LLMGenerationResponse(
            content = message?.optString("content").orEmpty(),
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
    private fun String.toOpenAIStreamEvent(): LLMStreamEvent? {
        if (!startsWith("data:")) return null
        val data = removePrefix("data:").trim()
        if (data == "[DONE]") return LLMStreamEvent.Finished(rawChunk = this)
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return null
        val delta = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
            .orEmpty()
        if (delta.isBlank()) return null
        return LLMStreamEvent.Delta(content = delta, rawChunk = data)
    }

    /**
     * 读取可空整数字段。
     */
    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }
}
