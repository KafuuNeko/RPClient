package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
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
        return raw.toAnthropicResponse(
            fallbackModel = model,
            includeReasoningInContent = request.includeReasoningInContent
        )
    }

    /**
     * Anthropic Messages 流式调用，解析 content_block_delta 事件中的 text。
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
                    parseAnthropicStreamParts(line).forEach { part ->
                        partMapper.map(part).forEach { emit(it) }
                    }
                }
                // 兼容未发送 message_stop 便关闭连接的代理服务
                partMapper.finish().forEach { emit(it) }
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
        val finalPayload = payload.withRequestBodyExtensions(mProvider, request)

        return LLMHttpRequest(
            request = Request.Builder()
                .url("${mProvider.normalizedBaseUrl()}/v1/messages")
                .post(finalPayload.toRequestBody())
                .header("x-api-key", mProvider.apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .applyProviderHeaders(mProvider)
                .build(),
            payloadJson = finalPayload.toString()
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
    private fun String.toAnthropicResponse(
        fallbackModel: String,
        includeReasoningInContent: Boolean
    ): LLMGenerationResponse {
        val json = JSONObject(this)
        val blocks = json.optJSONArray("content")
        val content = blocks?.joinTextFields(type = "text").orEmpty()
        val reasoningContent = blocks
            ?.joinStringFields(field = "thinking", type = "thinking")
            .orEmpty()
        return LLMGenerationResponse(
            content = if (includeReasoningInContent) {
                mergeReasoningContent(reasoningContent, content)
            } else {
                content
            },
            model = json.optString("model", fallbackModel),
            provider = mProvider.providerType,
            usage = parseAnthropicUsage(this),
            reasoningContent = reasoningContent,
            finishReason = json.optString("stop_reason").takeIf { it.isNotBlank() },
            rawResponse = this
        )
    }

}

/** 解析 Anthropic Messages SSE 行中的 thinking、text 与完成事件。 */
internal fun parseAnthropicStreamParts(line: String): List<LLMProviderStreamPart> {
    if (!line.startsWith("data:")) return emptyList()
    val data = line.removePrefix("data:").trim()
    val json = parseStreamJsonObject(data) ?: return emptyList()
    if (json.cleanString("type") == "message_stop") {
        return listOf(LLMProviderStreamPart.Finished(rawChunk = data))
    }
    val delta = json.objectOrNull("delta")
    val contentBlock = json.objectOrNull("content_block")
    val message = json.objectOrNull("message")
    val usage = json.anthropicUsage()
        ?: message?.anthropicUsage()
    val model = message?.cleanString("model")
    return buildList {
        usage?.let { add(LLMProviderStreamPart.Usage(it, model)) }
        // content_block_start 可能携带首段内容，不能只等待后续 delta
        contentBlock?.toAnthropicProviderPart(data)?.let(::add)
        delta?.toAnthropicProviderPart(data)?.let(::add)
        delta?.cleanString("stop_reason")?.takeIf { it.isNotBlank() }?.let {
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

/** 解析 Anthropic 用量，并把缓存创建与命中 Token 纳入输入总量。 */
internal fun parseAnthropicUsage(value: String): LLMUsage? {
    return parseStreamJsonObject(value)?.anthropicUsage()
}

private fun JsonObject.anthropicUsage(): LLMUsage? {
    return objectOrNull("usage")?.anthropicUsageFromContainer()
}

private fun JsonObject.anthropicUsageFromContainer(): LLMUsage? {
    val directInput = intOrNull("input_tokens")
    val cacheCreation = intOrNull("cache_creation_input_tokens")
    val cacheRead = intOrNull("cache_read_input_tokens")
    val output = intOrNull("output_tokens")
    if (directInput == null && cacheCreation == null && cacheRead == null && output == null) {
        return null
    }
    // Anthropic 将未缓存、缓存创建和缓存命中的输入拆分上报，统计时需要重新合并
    val cached = if (cacheCreation != null || cacheRead != null) {
        (cacheCreation ?: 0) + (cacheRead ?: 0)
    } else {
        null
    }
    val input = if (directInput != null || cached != null) {
        (directInput ?: 0) + (cached ?: 0)
    } else {
        null
    }
    return LLMUsage(
        promptTokens = input,
        completionTokens = output,
        totalTokens = if (input != null && output != null) input + output else null,
        cachedPromptTokens = cached
    )
}

private fun JsonObject.toAnthropicProviderPart(rawChunk: String): LLMProviderStreamPart? {
    return when (cleanString("type")) {
        "thinking", "thinking_delta" -> cleanString("thinking")
            .takeIf { it.isNotBlank() }
            ?.let { LLMProviderStreamPart.Reasoning(it, rawChunk) }
        "text", "text_delta" -> cleanString("text")
            .takeIf { it.isNotBlank() }
            ?.let { LLMProviderStreamPart.Text(it, rawChunk) }
        else -> cleanString("text")
            .takeIf { it.isNotBlank() }
            ?.let { LLMProviderStreamPart.Text(it, rawChunk) }
    }
}
