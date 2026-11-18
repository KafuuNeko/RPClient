package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.LLMEmptyResponseException
import me.kafuuneko.rpclient.libs.llm.LLMResponseException
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMReasoningKind
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMUsage

/**
 * Interactions 普通回复的响应边界。
 * - 仅提取角色正文与可展示的思考摘要，服务端步骤及签名不成为对话。
 * - 完整响应和 SSE 共用状态校验与用量语义。
 */
internal object GeminiInteractionsResponseCodec {
    /** 解析一次完整生成，保留实际模型、原始空白和服务端用量。 */
    fun decode(
        raw: String,
        provider: LLMProviderConfig,
        includeReasoning: Boolean
    ): LLMGenerationResponse {
        val response =
            parseStreamJsonObject(raw) ?: throw LLMResponseException("invalid_interaction")
        val reason = finishReason(response)
        val steps = response.arrayOrNull("steps") ?: throw LLMResponseException("missing_steps")
        // 共用流式映射器，连续输出之间的摘要按原位置展开，保留正文首尾空白。
        val parts = steps.mapNotNull { element ->
            val step = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw LLMResponseException("invalid_step")
            displayPart(step, raw = "")
        }
        if (parts.filterIsInstance<LLMProviderStreamPart.Text>()
                .none { it.content.isNotBlank() }
        ) throw LLMEmptyResponseException()
        val reasoning = parts.filterIsInstance<LLMProviderStreamPart.Reasoning>()
            .joinToString("") { it.content }
        val mapper = LLMStreamPartMapper(
            includeReasoningInContent = includeReasoning,
            captureReasoning = false
        )
        val content = (parts.flatMap(mapper::map) + mapper.finish())
            .filterIsInstance<LLMStreamEvent.Delta>().joinToString("") { it.content }
        return LLMGenerationResponse(
            content = content,
            reasoningContent = reasoning,
            model = response.cleanString("model").ifBlank { provider.model },
            provider = provider.providerType,
            usage = usage(response),
            finishReason = reason,
            rawResponse = raw,
        )
    }

    /** 解析单步可展示的初始内容，客户端函数调用由普通生成边界显式拒绝。 */
    fun displayPart(step: JsonObject, raw: String): LLMProviderStreamPart? =
        when (step.cleanString("type")) {
            "model_output" -> step.textParts("content").takeIf { it.isNotEmpty() }
                ?.let { LLMProviderStreamPart.Text(it, raw) }

            "thought" -> step.textParts("summary").takeIf { it.isNotEmpty() }
                ?.let { LLMProviderStreamPart.Reasoning(it, raw, LLMReasoningKind.Summary) }

            "function_call" -> throw LLMResponseException("tool_output_required")
            "" -> throw LLMResponseException("invalid_step_type")
            else -> null
        }

    /** 仅允许可用于普通回复的终态；尚需续接、失败或取消时保留失败语义。 */
    fun finishReason(response: JsonObject): String {
        response.objectOrNull("error")?.let { throw LLMResponseException(it.cleanString("code")) }
        return when (val status = response.cleanString("status")) {
            "completed" -> status
            // v1 没有给出完整结果时不推进摘要覆盖边界，也不把不完整正文记作成功请求。
            "incomplete" -> throw LLMResponseException("incomplete_interaction")
            "requires_action" -> throw LLMResponseException("tool_output_required")
            else -> throw LLMResponseException("invalid_interaction_status")
        }
    }

    /** 上报的输出和思考分项分别计费，缓存已包含在输入内。 */
    fun usage(response: JsonObject): LLMUsage? {
        val counts = response.objectOrNull("usage") ?: return null
        val input = counts.intOrNull("total_input_tokens")
        val output = counts.intOrNull("total_output_tokens")
        val thought = counts.intOrNull("total_thought_tokens")
        return LLMUsage(
            promptTokens = input,
            completionTokens = output?.let {
                (it.toLong() + (thought ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            },
            reasoningTokens = thought,
            totalTokens = counts.intOrNull("total_tokens"),
            cachedPromptTokens = counts.intOrNull("total_cached_tokens"),
        )
    }

    /** 只读文本块，字面字符串和空白按原样交付。 */
    private fun JsonObject.textParts(field: String): String = buildString {
        arrayOrNull(field)?.forEach { value ->
            val part = value.takeIf { it.isJsonObject }?.asJsonObject
            if (part?.cleanString("type") == "text") append(part.interactionText())
        }
    }
}

/** 文本必须按字符串读取，字面量 null 也是合法正文。 */
internal fun JsonObject.interactionText(): String = get("text")
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    ?: throw LLMResponseException("invalid_text")
