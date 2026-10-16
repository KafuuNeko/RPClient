package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningKind
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage

/** 输出内容的原生位置，用于对流式增量、内容快照和最终响应去重。 */
internal data class ResponsesContentKey(val outputIndex: Int, val contentIndex: Int, val type: String)

/** Responses 可展示片段，保留推理摘要与正文的语义边界。 */
internal data class ResponsesContent(val key: ResponsesContentKey, val text: String) {
    val isReasoning: Boolean get() = key.type == "summary_text" || key.type == "reasoning_text"
    val reasoningKind: LLMReasoningKind
        get() = if (key.type == "summary_text") LLMReasoningKind.Summary else LLMReasoningKind.Detailed
}

/**
 * Responses 完整响应解码器，同时为流式终态提供统一的状态校验。
 * - 按 Item 和内容类型提取正文、拒绝内容及推理摘要。
 * - 未完成、错误和等待客户端工具结果不能被当作普通成功回复。
 */
internal object OpenAIResponsesResponseCodec {
    /** 解析完整 HTTP 响应，保留原生结束原因与服务端用量。 */
    fun decode(
        raw: String,
        fallbackModel: String,
        provider: LLMProviderType,
        includeReasoningInContent: Boolean
    ): LLMGenerationResponse {
        val response = parseStreamJsonObject(raw) ?: throw LLMResponseException("invalid_response")
        val finishReason = finishReason(response)
        val parts = contents(response)
        val text = parts.filterNot { it.isReasoning }.joinToString("") { it.text }
        val reasoning = parts.filter { it.isReasoning }.joinToString("") { it.text }
        // 只有推理而没有正文时仍是空回复，尤其要保留耗尽输出预算的诊断。
        if (text.isBlank()) throw LLMEmptyResponseException(finishReason == "max_output_tokens")
        return LLMGenerationResponse(
            content = if (includeReasoningInContent && reasoning.isNotEmpty()) {
                "<think>\n$reasoning\n</think>\n\n$text"
            } else text,
            model = response.cleanString("model").ifBlank { fallbackModel },
            provider = provider,
            usage = usage(response),
            reasoningContent = reasoning,
            finishReason = finishReason,
            rawResponse = raw
        )
    }

    /** 校验响应终态；长度限制与内容过滤保留可用正文，其余未完成状态明确失败。 */
    fun finishReason(response: JsonObject): String {
        response.objectOrNull("error")?.let { throw error(it) }
        return when (response.cleanString("status")) {
            "completed" -> "completed"
            "incomplete" -> response.objectOrNull("incomplete_details")?.cleanString("reason")
                ?.takeIf { it == "max_output_tokens" || it == "content_filter" }
                ?: throw LLMResponseException("incomplete_response")
            "failed", "cancelled" -> throw LLMResponseException("response_failed")
            else -> throw LLMResponseException("invalid_response_status")
        }
    }

    /** 收集完整输出数组；未知的服务端工具 Item 不影响已知文本类型的解析。 */
    fun contents(response: JsonObject): List<ResponsesContent> {
        val output = response.arrayOrNull("output") ?: throw LLMResponseException("invalid_response")
        return output.flatMapIndexed { index, value ->
            if (!value.isJsonObject) throw LLMResponseException("invalid_output_item")
            itemContents(value.asJsonObject, index)
        }
    }

    /** 解析已完成 Item，客户端工具调用必须显式报告等待执行，不能静默丢弃。 */
    fun itemContents(item: JsonObject, outputIndex: Int): List<ResponsesContent> {
        val type = item.cleanString("type")
        if (type in CLIENT_TOOL_ITEMS) throw LLMResponseException("tool_output_required")
        // 工具调用前的进度说明不属于最终回复，不能作为 final_answer 写入本地历史。
        if (type == "message" && item.cleanString("phase") == "commentary") return emptyList()
        // 只解析可展示字段，encrypted_content、工具参数及二进制结果不进入聊天正文。
        return when (type) {
            "message" -> contentArray(item, "content", outputIndex, setOf("output_text", "refusal"))
            "reasoning" -> contentArray(item, "summary", outputIndex, setOf("summary_text")) +
                contentArray(item, "content", outputIndex, setOf("reasoning_text"))
            else -> emptyList()
        }
    }

    /** 读取服务端用量，推理及缓存是总量的组成部分，不重复累加。 */
    fun usage(response: JsonObject): LLMUsage? {
        val usage = response.objectOrNull("usage") ?: return null
        return LLMUsage(
            promptTokens = usage.intOrNull("input_tokens"),
            completionTokens = usage.intOrNull("output_tokens"),
            totalTokens = usage.intOrNull("total_tokens"),
            cachedPromptTokens = usage.objectOrNull("input_tokens_details")?.intOrNull("cached_tokens"),
            reasoningTokens = usage.objectOrNull("output_tokens_details")?.intOrNull("reasoning_tokens")
        )
    }

    /** 将服务端错误压缩成安全错误码；原始错误只随受控请求日志保存。 */
    fun error(payload: JsonObject): LLMResponseException {
        val error = payload.objectOrNull("error") ?: payload
        return LLMResponseException(error.cleanString("code").ifBlank { error.cleanString("type") })
    }

    /** 仅提取已知文字类型，原生内容位置用于和流式事件的索引对应。 */
    private fun contentArray(
        item: JsonObject,
        field: String,
        outputIndex: Int,
        types: Set<String>
    ): List<ResponsesContent> = item.arrayOrNull(field)?.mapIndexedNotNull { index, value ->
        val part = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapIndexedNotNull null
        val type = part.cleanString("type").takeIf { it in types } ?: return@mapIndexedNotNull null
        ResponsesContent(
            ResponsesContentKey(outputIndex, index, type),
            part.responsesText(if (type == "refusal") "refusal" else "text")
        )
    }.orEmpty()

    private val CLIENT_TOOL_ITEMS = setOf(
        "function_call", "custom_tool_call", "computer_call", "local_shell_call", "shell_call",
        "apply_patch_call", "mcp_approval_request"
    )
}

/** 官方正文中的空白和字面量 null 都是有效文本，不能套用兼容网关的清理规则。 */
internal fun JsonObject.responsesText(name: String): String = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
