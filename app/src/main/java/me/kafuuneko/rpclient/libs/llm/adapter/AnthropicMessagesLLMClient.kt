package me.kafuuneko.rpclient.libs.llm.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class AnthropicMessagesLLMClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProviderConfig
) : LLMClient {
    /**
     * Anthropic Messages 非流式调用，适用于 Claude 官方接口。
     */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        val model = request.model ?: mProvider.model
        val httpRequest = buildRequest(request, model, stream = false)
        val raw = mOkHttpClient.await(httpRequest)
        return raw.toAnthropicResponse(model)
    }

    /**
     * Anthropic Messages 流式调用，解析 content_block_delta 事件中的 text。
     */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        val model = request.model ?: mProvider.model
        return mOkHttpClient.streamLines(buildRequest(request, model, stream = true))
            .mapNotNull { line -> line.toAnthropicStreamEvent() }
    }

    /**
     * 构建 Anthropic Messages 请求体。stream 参数控制是否返回 SSE。
     */
    private fun buildRequest(
        request: LLMGenerationRequest,
        model: String,
        stream: Boolean
    ): Request {
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", request.options.maxTokens ?: mProvider.maxTokens)
            .put("top_p", request.options.topP ?: mProvider.topP)
            .put("temperature", request.options.temperature ?: mProvider.temperature)
            .put("messages", request.messages.toAnthropicMessages())
            .put("stream", stream)
        val systemPrompt = request.messages
            .filter { it.role == LLMMessageRole.System }
            .joinToString("\n\n") { it.content }
        if (systemPrompt.isNotBlank()) payload.put("system", systemPrompt)
        if (request.options.stop.isNotEmpty()) payload.put("stop_sequences", request.options.stop.toJsonArray())

        return Request.Builder()
            .url("${mProvider.normalizedBaseUrl()}/v1/messages")
            .post(payload.toRequestBody())
            .header("x-api-key", mProvider.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .applyProviderHeaders(mProvider)
            .build()
    }

    /**
     * 转换通用消息为 Anthropic messages 数组。
     */
    private fun List<LLMMessage>.toAnthropicMessages(): JSONArray {
        return JSONArray().also { array ->
            filter { it.role != LLMMessageRole.System }.forEach { message ->
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
            content = json.optJSONArray("content")?.joinText().orEmpty(),
            model = json.optString("model", fallbackModel),
            provider = mProvider.providerType,
            rawResponse = this
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
        val text = delta.optString("text")
        if (text.isBlank()) return null
        return LLMStreamEvent.Delta(content = text, rawChunk = data)
    }

    /**
     * 拼接 Anthropic content block 中的 text 字段。
     */
    private fun JSONArray.joinText(): String {
        return buildString {
            for (index in 0 until length()) {
                val block = optJSONObject(index) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }
}
