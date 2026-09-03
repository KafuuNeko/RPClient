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
 * 无后处理且 Tokenizer 支持增量统计时会缓存逐条 Token 数并只排序一次；其余情况因
 * 合并、占位符或自定义统计可能在移除后改变结果，继续逐轮精确后处理和复算。成功时生成标记为
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
     * - 过滤空消息，并根据后处理模式选择缓存增量裁剪或逐轮精确复算；
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

        val environment = PromptFinalizationEnvironment(
            tokenizer = tokenizerFor(provider),
            model = model,
            options = options,
            includeReasoningInContent = includeReasoningInContent,
            captureReasoning = captureReasoning,
            maxContextTokens = maxContextTokens,
            maxResponseTokens = maxResponseTokens,
            promptBudget = promptBudget,
            postProcessingMode = postProcessingMode
        )
        val filteredDrafts = drafts.filter { it.content.isNotBlank() }
        val omitted = preOmittedItems.toMutableList()
        if (
            postProcessingMode == PromptPostProcessingMode.None &&
            environment.tokenizer.supportsIncrementalMessageCounting
        ) {
            return finalizeWithoutPostProcessing(
                drafts = filteredDrafts,
                omitted = omitted,
                environment = environment
            )
        }
        val kept = filteredDrafts.toMutableList()

        // 迭代裁剪循环：每次淘汰消息后重新执行后处理与统计（因合并可能改变 Token 总数）
        while (true) {
            val processed = kept.postProcess(
                postProcessingMode,
                strictPromptPlaceholder,
                postProcessingNames
            )
            val messages = processed.map { LLMMessage(it.role, it.content) }
            val finalTokenCount = environment.tokenizer.countMessages(messages)
            // 满足输入预算，构建最终请求与检查报告
            if (finalTokenCount <= promptBudget) {
                return createFinalizationResult(
                    processed = processed,
                    messages = messages,
                    finalTokenCount = finalTokenCount,
                    omitted = omitted,
                    environment = environment
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
                    tokenCount = environment.tokenizer.countText(removed.content),
                    reason = PromptOmissionReason.ContextBudget
                )
            }
        }
    }

    /**
     * 在消息结构不会被后处理改写时，缓存逐条 Token 数并按原有优先级顺序裁剪。
     *
     * 移除顺序仍然由保留优先级和原始位置共同决定；最终请求、检查项和遗漏项的顺序
     * 与逐轮实现保持一致。
     */
    private fun finalizeWithoutPostProcessing(
        drafts: List<PromptMessageDraft>,
        omitted: MutableList<PromptOmittedItem>,
        environment: PromptFinalizationEnvironment
    ): PromptFinalizationResult {
        val countedDrafts = drafts.mapIndexed { index, draft ->
            val message = LLMMessage(draft.role, draft.content)
            CountedPromptDraft(
                index = index,
                draft = draft,
                message = message,
                messageTokenCount = environment.tokenizer.countMessage(message),
                contentTokenCount = environment.tokenizer.countText(draft.content)
            )
        }
        // 完整列表固定开销只探测一次，后续每次移除直接减去已缓存的消息成本
        var remainingCount = countedDrafts.size
        var currentTokenCount = countedDrafts.sumOf { it.messageTokenCount }
        if (countedDrafts.isNotEmpty()) {
            currentTokenCount += environment.tokenizer.countMessages(
                listOf(countedDrafts.first().message)
            ) - countedDrafts.first().messageTokenCount
        }
        val removedIndexes = BooleanArray(countedDrafts.size)
        val removalOrder = countedDrafts
            .filter { it.draft.canDrop }
            .sortedWith(
                compareBy<CountedPromptDraft> { it.draft.retentionPriority }
                    .thenBy { it.index }
            )
        // 按与旧实现相同的顺序淘汰，并在首次满足预算时一次性构建结果
        var removalIndex = 0
        while (currentTokenCount > environment.promptBudget) {
            val removed = removalOrder.getOrNull(removalIndex)
                ?: throw PromptBudgetExceededException(
                    currentTokenCount,
                    environment.promptBudget
                )
            removalIndex += 1
            removedIndexes[removed.index] = true
            remainingCount -= 1
            currentTokenCount = if (remainingCount == 0) {
                0
            } else {
                currentTokenCount - removed.messageTokenCount
            }
            removed.draft.sources.forEach { source ->
                omitted += PromptOmittedItem(
                    source = source,
                    tokenCount = removed.contentTokenCount,
                    reason = PromptOmissionReason.ContextBudget
                )
            }
        }
        val retained = countedDrafts.filterNot { removedIndexes[it.index] }
        val processed = retained.map {
            TrackedPromptMessage(
                role = it.draft.role,
                content = it.draft.content,
                sources = it.draft.sources
            )
        }
        return createFinalizationResult(
            processed = processed,
            messages = retained.map { it.message },
            finalTokenCount = currentTokenCount,
            omitted = omitted,
            environment = environment,
            itemTokenCounts = retained.map { it.messageTokenCount }
        )
    }

    /** 使用统一数据源创建最终请求与 Prompt 检查报告。 */
    private fun createFinalizationResult(
        processed: List<TrackedPromptMessage>,
        messages: List<LLMMessage>,
        finalTokenCount: Int,
        omitted: List<PromptOmittedItem>,
        environment: PromptFinalizationEnvironment,
        itemTokenCounts: List<Int>? = null
    ): PromptFinalizationResult {
        // 请求和检查报告共用相同的最终消息序列，避免调试结果与实际提交内容分叉
        return PromptFinalizationResult(
            request = LLMGenerationRequest(
                messages = messages,
                model = environment.model,
                options = environment.options,
                includeReasoningInContent = environment.includeReasoningInContent,
                captureReasoning = environment.captureReasoning,
                isPromptFinalized = true
            ),
            inspection = PromptInspection(
                model = environment.model.orEmpty(),
                tokenizerName = environment.tokenizer.name,
                tokenizerStrategy = environment.tokenizer.strategy,
                tokenizerReservePercent = environment.tokenizer.reservePercent,
                postProcessingMode = environment.postProcessingMode,
                contextLimit = environment.maxContextTokens,
                responseReserve = environment.maxResponseTokens,
                promptBudget = environment.promptBudget,
                finalTokenCount = finalTokenCount,
                // 增量路径复用缓存；精确回退路径只在最终成功结果上统计一次检查项
                items = processed.mapIndexed { index, message ->
                    PromptInspectionItem(
                        index = index + 1,
                        role = message.role,
                        sources = message.sources.distinct(),
                        tokenCount = itemTokenCounts?.get(index)
                            ?: environment.tokenizer.countMessage(messages[index]),
                        content = message.content
                    )
                },
                omittedItems = omitted
            )
        )
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

/** 单条未后处理草稿及其一次性 Token 统计结果。 */
private data class CountedPromptDraft(
    /** 空消息过滤后的稳定位置。 */
    val index: Int,
    /** 尚未执行后处理的原始草稿。 */
    val draft: PromptMessageDraft,
    /** 草稿对应的模型消息。 */
    val message: LLMMessage,
    /** 包含角色与模板开销的整条消息 Token 数。 */
    val messageTokenCount: Int,
    /** 遗漏检查项展示使用的纯正文 Token 数。 */
    val contentTokenCount: Int
)

/** 一次最终化过程共享的模型配置、预算和 Tokenizer。 */
private data class PromptFinalizationEnvironment(
    /** 当前模型配置解析出的 Tokenizer。 */
    val tokenizer: PromptTokenizer,
    /** 实际提交给服务的模型名称。 */
    val model: String?,
    /** 实际提交给服务的生成参数。 */
    val options: LLMGenerationOptions,
    /** 是否把推理内容合并到正文。 */
    val includeReasoningInContent: Boolean,
    /** 是否单独捕获推理内容。 */
    val captureReasoning: Boolean,
    /** 模型配置的完整上下文上限。 */
    val maxContextTokens: Int,
    /** 为模型回复保留的 Token 数。 */
    val maxResponseTokens: Int,
    /** 扣除回复预留后的 Prompt 可用预算。 */
    val promptBudget: Int,
    /** 本次最终化使用的消息后处理模式。 */
    val postProcessingMode: PromptPostProcessingMode
)
