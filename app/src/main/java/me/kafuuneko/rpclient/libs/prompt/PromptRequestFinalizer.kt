package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 最终可发送请求及其同源检查报告。 */
data class PromptFinalizationResult(
    val request: LLMGenerationRequest,
    val inspection: PromptInspection
)

/**
 * 核心不可丢弃内容本身已超过输入预算。
 *
 * 此时继续静默裁剪会破坏角色设定，因此终止构建并由界面提示用户调整上下文。
 */
class PromptBudgetExceededException(
    val requiredTokens: Int,
    val promptBudget: Int
) : IllegalStateException(
    "Prompt requires $requiredTokens tokens, but only $promptBudget input tokens are available. " +
        "Shorten the core prompt or increase the context limit."
)

/**
 * Prompt 流水线的最终化阶段。
 *
 * 依次执行消息后处理、Token 统计和按优先级裁剪，成功时生成标记为
 * [LLMGenerationRequest.isPromptFinalized] 的请求，防止 Repository 重复改写。
 */
class PromptRequestFinalizer(
    private val mTokenizerResolver: PromptTokenizerResolver = PromptTokenizerRegistry()
) {
    /** 为供应商选择与预算统计一致的 Tokenizer。 */
    fun tokenizerFor(provider: LLMProvider?): PromptTokenizer {
        return mTokenizerResolver.resolve(provider)
    }

    /**
     * 将消息草稿收敛为不超过上下文预算的最终请求。
     *
     * 每次移除草稿后都重新执行后处理和统计，因为消息合并会改变最终 Token 数量。
     */
    fun finalize(
        drafts: List<PromptMessageDraft>,
        provider: LLMProvider?,
        model: String?,
        options: LLMGenerationOptions,
        includeReasoningInContent: Boolean,
        maxContextTokens: Int,
        maxResponseTokens: Int,
        postProcessingMode: PromptPostProcessingMode,
        strictPromptPlaceholder: String,
        postProcessingNames: PromptPostProcessingNames = PromptPostProcessingNames(),
        preOmittedItems: List<PromptOmittedItem> = emptyList()
    ): PromptFinalizationResult {
        val promptBudget = maxContextTokens - maxResponseTokens
        require(maxContextTokens > 0) { "Context token limit must be greater than zero." }
        require(maxResponseTokens > 0) { "Response token reserve must be greater than zero." }
        require(promptBudget > 0) {
            "Response token reserve must be smaller than the context token limit."
        }

        val tokenizer = tokenizerFor(provider)
        val kept = drafts.filter { it.content.isNotBlank() }.toMutableList()
        val omitted = preOmittedItems.toMutableList()

        while (true) {
            val processed = kept.postProcess(
                postProcessingMode,
                strictPromptPlaceholder,
                postProcessingNames
            )
            val messages = processed.map { LLMMessage(it.role, it.content) }
            val finalTokenCount = tokenizer.countMessages(messages)
            if (finalTokenCount <= promptBudget) {
                return PromptFinalizationResult(
                    request = LLMGenerationRequest(
                        messages = messages,
                        model = model,
                        options = options,
                        includeReasoningInContent = includeReasoningInContent,
                        isPromptFinalized = true
                    ),
                    inspection = PromptInspection(
                        model = model.orEmpty(),
                        tokenizerName = tokenizer.name,
                        tokenizerStrategy = tokenizer.strategy,
                        postProcessingMode = postProcessingMode,
                        contextLimit = maxContextTokens,
                        responseReserve = maxResponseTokens,
                        promptBudget = promptBudget,
                        finalTokenCount = finalTokenCount,
                        items = processed.mapIndexed { index, message ->
                            val llmMessage = LLMMessage(message.role, message.content)
                            PromptInspectionItem(
                                index = index + 1,
                                role = message.role,
                                sources = message.sources.distinct(),
                                tokenCount = tokenizer.countMessage(llmMessage),
                                content = message.content
                            )
                        },
                        omittedItems = omitted
                    )
                )
            }

            val removable = kept.withIndex()
                .filter { it.value.canDrop }
                .minWithOrNull(
                    compareBy<IndexedValue<PromptMessageDraft>> { it.value.retentionPriority }
                        .thenBy { it.index }
                )
            if (removable == null) {
                throw PromptBudgetExceededException(finalTokenCount, promptBudget)
            }
            val removed = kept.removeAt(removable.index)
            removed.sources.forEach { source ->
                omitted += PromptOmittedItem(
                    source = source,
                    tokenCount = tokenizer.countText(removed.content),
                    reason = PromptOmissionReason.ContextBudget
                )
            }
        }
    }

    private fun List<PromptMessageDraft>.postProcess(
        mode: PromptPostProcessingMode,
        strictPromptPlaceholder: String,
        names: PromptPostProcessingNames
    ): List<TrackedPromptMessage> {
        return postProcessTrackedMessages(
            messages = map {
                TrackedPromptMessage(
                    role = it.role,
                    content = it.content,
                    sources = it.sources
                )
            },
            mode = mode,
            strictPromptPlaceholder = strictPromptPlaceholder,
            names = names
        )
    }
}
