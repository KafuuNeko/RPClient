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
    /**
     * 解析一次完整生成，保留实际模型、原始空白和服务端用量。
     * @param raw 服务端完整响应，仅供协议解析及受控调试流程使用。
     * @param provider 本次实际使用的模型配置，用于补全缺失的模型名。
     * @param includeReasoning 是否将可展示思考摘要并入正文。
     * @return 已校验状态的正文、思考摘要和用量。
     * @throws LLMResponseException 响应结构无效或需要客户端工具续接。
     * @throws LLMEmptyResponseException 响应不包含有效正文。
     */
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

    /**
     * 解析单步可展示的初始内容，客户端函数调用由普通生成边界显式拒绝。
     * @param step 保持服务端原始顺序的一个步骤。
     * @param raw 对应的调试数据，非流式聚合时传空字符串。
     * @return 正文或思考摘要片段；没有可展示内容时返回 null。
     * @throws LLMResponseException 步骤类型无效或要求客户端工具结果。
     */
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

    /**
     * 保留已结束回复的原生完整性状态，尚需续接、失败或取消时继续抛出协议错误。
     * @param response 完整响应或流式终态中的 interaction 对象。
     * @return 服务端的 completed 或 incomplete 状态。
     * @throws LLMResponseException 交互未结束、失败或需要客户端工具续接。
     */
    fun finishReason(response: JsonObject): String {
        response.objectOrNull("error")?.let { throw LLMResponseException(it.cleanString("code")) }
        return when (val status = response.cleanString("status")) {
            // incomplete 可能由长度限制导致，保留正文但不猜测服务端未给出的具体原因。
            "completed", "incomplete" -> status
            "requires_action" -> throw LLMResponseException("tool_output_required")
            else -> throw LLMResponseException("invalid_interaction_status")
        }
    }

    /**
     * 映射服务端用量，输出合并独立的思考分项，缓存不重复计入输入。
     * @param response 包含可选 usage 对象的交互响应。
     * @return 统一用量；服务端没有上报时返回 null。
     */
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
