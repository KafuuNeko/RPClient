package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmittedItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 最终可发送请求及其同源检查报告。 */
data class PromptFinalizationResult(
    /** 经过业务层组装、准备提交给模型服务的请求。 */
    val request: LLMGenerationRequest,
    /** 与实际请求一致、供 Prompt 检查器展示的构建明细。 */
    val inspection: PromptInspection
)

/**
 * 不可丢弃的 Prompt 内容本身已超过输入预算。
 *
 * 此时继续静默裁剪会破坏角色设定，因此终止构建并由界面提示用户调整上下文。
 */
class PromptBudgetExceededException(
    val requiredTokens: Int,
    val promptBudget: Int
) : IllegalStateException(
    "Prompt requires $requiredTokens tokens, but only $promptBudget input tokens are available. " +
        "Shorten required prompt content or ignored-budget World Info, or increase the context limit."
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
    /** 为模型配置选择与预算统计一致的 Tokenizer。 */
    fun tokenizerFor(provider: LLMProvider?): PromptTokenizer {
        return mTokenizerResolver.resolve(provider)
    }

    /**
     * 将消息草稿收敛为不超过上下文预算的最终请求。
     *
     * 核心步骤：
     * - 校验上下文上限与回复 Token 预留的合法性；
     * - 过滤空消息并循环执行“消息后处理 -> Token 统计 -> 超额判定”；
     * - 若满足预算则封装并返回 [PromptFinalizationResult] 及详细检查报告；
     * - 若超额则按保留优先级（[me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft.retentionPriority]）最低者淘汰一条非必需消息并重新计算；
     * - 若所有可丢弃消息均淘汰后依然超限，则抛出 [PromptBudgetExceededException]。
     */
    fun finalize(
        drafts: List<PromptMessageDraft>,
        provider: LLMProvider?,
        model: String?,
        options: LLMGenerationOptions,
        includeReasoningInContent: Boolean,
        captureReasoning: Boolean = includeReasoningInContent,
        maxContextTokens: Int,
        maxResponseTokens: Int,
        postProcessingMode: PromptPostProcessingMode,
        strictPromptPlaceholder: String,
        postProcessingNames: PromptPostProcessingNames = PromptPostProcessingNames(),
        preOmittedItems: List<PromptOmittedItem> = emptyList()
    ): PromptFinalizationResult {
        // 计算扣除回复预留后的输入 Prompt 预算
        val promptBudget = maxContextTokens - maxResponseTokens
        require(maxContextTokens > 0) { "Context token limit must be greater than zero." }
        require(maxResponseTokens > 0) { "Response token reserve must be greater than zero." }
        require(promptBudget > 0) {
            "Response token reserve must be smaller than the context token limit."
        }

        val tokenizer = tokenizerFor(provider)
        val kept = drafts.filter { it.content.isNotBlank() }.toMutableList()
        val omitted = preOmittedItems.toMutableList()

        // 迭代裁剪循环：每次淘汰消息后重新执行后处理与统计（因合并可能改变 Token 总数）
        while (true) {
            val processed = kept.postProcess(
                postProcessingMode,
                strictPromptPlaceholder,
                postProcessingNames
            )
            val messages = processed.map { LLMMessage(it.role, it.content) }
            val finalTokenCount = tokenizer.countMessages(messages)
            // 满足输入预算，构建最终请求与检查报告
            if (finalTokenCount <= promptBudget) {
                return PromptFinalizationResult(
                    request = LLMGenerationRequest(
                        messages = messages,
                        model = model,
                        options = options,
                        includeReasoningInContent = includeReasoningInContent,
                        captureReasoning = captureReasoning,
                        isPromptFinalized = true
                    ),
                    inspection = PromptInspection(
                        model = model.orEmpty(),
                        tokenizerName = tokenizer.name,
                        tokenizerStrategy = tokenizer.strategy,
                        tokenizerReservePercent = tokenizer.reservePercent,
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

            // 超出预算：查找优先级最低的可丢弃消息
            val removable = kept.withIndex()
                .filter { it.value.canDrop }
                .minWithOrNull(
                    compareBy<IndexedValue<PromptMessageDraft>> { it.value.retentionPriority }
                        .thenBy { it.index }
                )
            // 无可丢弃消息，终止并抛出异常
            if (removable == null) {
                throw PromptBudgetExceededException(finalTokenCount, promptBudget)
            }
            // 移除消息并记录遗漏明细
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

    /** 内部扩展：将草稿列表执行带来源追踪的消息后处理。 */
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
