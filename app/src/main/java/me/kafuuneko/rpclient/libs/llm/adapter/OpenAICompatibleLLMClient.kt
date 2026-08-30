package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.LLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage
import me.kafuuneko.rpclient.libs.llm.model.resolveFor
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * OpenAI Chat Completions 兼容协议适配器。
 *
 * 除标准 content 外还兼容常见推理字段，并在启用推理展示时用 think 标签
 * 合并到正文，使流式与非流式结果保持一致。
 */
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
            mLLMRequestLogRepository.trySaveLog(mProvider, model, false, httpRequest.payloadJson, it)
        }.onFailure {
            mLLMRequestLogRepository.trySaveLog(mProvider, model, false, httpRequest.payloadJson, it.toErrorJson())
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
            // 构建带 stream=true 的 HTTP 请求与参数补丁
            val httpRequest = buildRequest(request, model, stream = true)
            val rawChunks = JSONArray()
            val partMapper = LLMStreamPartMapper(
                includeReasoningInContent = request.includeReasoningInContent,
                captureReasoning = request.captureReasoning
            )
            runCatching {
                // 逐行消费 SSE 数据流
                mOkHttpClient.streamLines(
                    request = httpRequest.request,
                    onConnected = { emit(LLMStreamEvent.Connected) }
                ).collect { line ->
                    rawChunks.put(line)
                    parseOpenAIStreamParts(line).forEach { part ->
                        partMapper.map(part).forEach { emit(it) }
                    }
                }
                // 兼容未发送完成块便直接关闭连接的模型服务
                partMapper.finish().forEach { emit(it) }
            }.onSuccess {
                // 记录成功的请求与完整 SSE 原始块日志
                mLLMRequestLogRepository.trySaveLog(mProvider, model, true, httpRequest.payloadJson, rawChunks.toString())
            }.onFailure {
                // 记录失败日志并向上层抛出
                mLLMRequestLogRepository.trySaveLog(mProvider, model, true, httpRequest.payloadJson, it.toErrorJson())
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
        val options = request.options.resolveFor(mProvider)
        // 组装通用请求体字段
        val payload = JSONObject()
            .put("model", model)
            .put("messages", request.messages.toOpenAIMessages())
            .put(
                openAICompatibleTokenLimitField(mProvider.providerType),
                options.maxTokens
            )
            .put("stream", stream)
        // 端点能力由模型配置显式声明
        if (stream && mProvider.requestStreamUsage) {
            payload.put("stream_options", JSONObject().put("include_usage", true))
        }
        options.temperature?.let { payload.put("temperature", it) }
        options.topP?.let { payload.put("top_p", it) }
        if (options.stop.isNotEmpty()) payload.put("stop", options.stop.toJsonArray())
        // 应用针对 OpenRouter / 自定义 JSON Patch 的扩展补丁
        val finalPayload = payload.withRequestBodyExtensions(mProvider, request)

        // 构造 OkHttp 请求对象
        return LLMHttpRequest(
            request = Request.Builder()
            .url("${mProvider.normalizedBaseUrl()}/chat/completions")
            .post(finalPayload.toRequestBody())
            .header("Authorization", "Bearer ${mProvider.apiKey}")
            .header("Content-Type", "application/json")
            .applyProviderHeaders(mProvider)
            .build(),
            payloadJson = finalPayload.toString()
        )
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
        val reasoningContent = message?.optReasoningContent().orEmpty()
        val content = message?.optContentString("content").orEmpty()
        return LLMGenerationResponse(
            content = mergeReasoningContent(
                reasoningContent = reasoningContent,
                content = content,
                includeReasoningInContent = includeReasoningInContent
            ),
            model = json.optString("model", fallbackModel),
            provider = mProvider.providerType,
            usage = parseOpenAIUsage(this),
            reasoningContent = reasoningContent,
            finishReason = choice.optCleanString("finish_reason"),
            rawResponse = this
        )
    }

    private fun mergeReasoningContent(
        reasoningContent: String,
        content: String,
        includeReasoningInContent: Boolean
    ): String {
        if (!includeReasoningInContent || reasoningContent.isBlank()) return content
        return "<think>\n$reasoningContent\n</think>\n\n$content".trim()
    }

}

/** 解析 OpenAI-compatible SSE 行并保留同一增量中的推理与正文。 */
internal fun parseOpenAIStreamParts(line: String): List<LLMProviderStreamPart> {
    if (!line.startsWith("data:")) return emptyList()
    val data = line.removePrefix("data:").trim()
    if (data == "[DONE]") {
        return listOf(LLMProviderStreamPart.Finished(rawChunk = line))
    }
    val json = parseStreamJsonObject(data) ?: return emptyList()
    val actualModel = json.cleanString("model")
    val usage = json.openAIUsage()
    val choice = json.arrayOrNull("choices")
        ?.firstOrNull()
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
    return buildList {
        usage?.let { add(LLMProviderStreamPart.Usage(it, actualModel)) }
        if (choice == null) return@buildList
        val deltaObject = choice.objectOrNull("delta")
        val finishReason = choice.cleanString("finish_reason")
        // 部分兼容网关会在同一个增量中同时返回推理和正文，两者都必须保留
        deltaObject?.reasoningContent()?.takeIf { it.isNotBlank() }?.let {
            add(LLMProviderStreamPart.Reasoning(it, data))
        }
        deltaObject?.cleanString("content")?.takeIf { it.isNotBlank() }?.let {
            add(LLMProviderStreamPart.Text(it, data))
        }
        finishReason.takeIf { it.isNotBlank() }?.let {
            add(
                LLMProviderStreamPart.Finished(
                    rawChunk = data,
                    finishReason = it,
                    model = actualModel,
                    terminal = false
                )
            )
        }
    }
}

/** 解析 OpenAI-compatible 完整响应中的标准用量与缓存、推理明细。 */
internal fun parseOpenAIUsage(value: String): LLMUsage? {
    return parseStreamJsonObject(value)?.openAIUsage()
}

private fun JsonObject.openAIUsage(): LLMUsage? {
    val usage = objectOrNull("usage") ?: return null
    return LLMUsage(
        promptTokens = usage.intOrNull("prompt_tokens"),
        completionTokens = usage.intOrNull("completion_tokens"),
        totalTokens = usage.intOrNull("total_tokens"),
        cachedPromptTokens = usage.objectOrNull("prompt_tokens_details")
            ?.intOrNull("cached_tokens"),
        reasoningTokens = usage.objectOrNull("completion_tokens_details")
            ?.intOrNull("reasoning_tokens")
    )
}

private fun JsonObject.reasoningContent(): String {
    return cleanString("reasoning_content")
        .ifBlank { cleanString("reasoning") }
        .ifBlank { cleanString("reasoningContent") }
}

private fun JSONObject.optReasoningContent(): String {
    return optContentString("reasoning_content")
        .ifBlank { optContentString("reasoning") }
        .ifBlank { optContentString("reasoningContent") }
}

private fun JSONObject.optCleanString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    val value = optString(name).trim()
    return if (value.equals("null", ignoreCase = true)) "" else value
}

/**
 * 读取 OpenAI Compatible 的文本字段。
 *
 * 部分网关会把缺失内容序列化为字符串 `"null"`，这里统一转为空串，避免该字面量
 * 出现在聊天正文或推理块中。
 */
internal fun JSONObject.optContentString(name: String): String {
    if (!has(name) || isNull(name)) return ""
    return cleanContentString(optString(name))
}

/**
 * 返回 OpenAI-compatible 请求的输出 Token 上限字段。
 *
 * OpenAI 官方接口已用 max_completion_tokens 取代 max_tokens；第三方兼容服务可能尚未
 * 实现新字段，因此只对供应商类型明确为 ChatGPT 的模型配置使用新名称。
 */
internal fun openAICompatibleTokenLimitField(providerType: LLMProviderType): String {
    return if (providerType == LLMProviderType.ChatGPT) {
        "max_completion_tokens"
    } else {
        "max_tokens"
    }
}
