package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.DEFAULT_STRICT_PROMPT_PLACEHOLDER
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 使用与故事续写相同的正文裁剪规则，构建单次剧情摘要请求。 */
class StorySummaryPromptBuilder(
    private val mContextSelector: StoryContextSelector,
    private val mRequestFinalizer: PromptRequestFinalizer
) {
    /**
     * 构建故事模式剧情摘要生成请求。
     *
     * 核心步骤：
     * - 校验故事正文非空；
     * - 计算扣除回复预留后的 Prompt 输入预算；
     * - 利用 [StoryContextSelector] 选取离正文末尾最近的有效上下文分块；
     * - 组装摘要指令、记忆草稿、现有摘要与正文 Chunk 草稿消息；
     * - 提交最终化流水线执行消息后处理与预算校验。
     */
    fun build(
        memory: String,
        currentSummary: String,
        content: String,
        provider: LLMProvider,
        userName: String,
        primaryCharacterName: String?,
    ): LLMGenerationRequest {
        require(content.isNotBlank()) { "Story manuscript cannot be blank" }
        // 校验并计算可用 Prompt 输入预算
        val responseTokens = AppModel.summaryResponseTokens.coerceAtLeast(1)
        val promptBudget = provider.contextTokens - responseTokens
        require(promptBudget > 0) {
            "Summary response token reserve must be smaller than the context token limit."
        }
        // 基于正文选择最近段落 Chunk
        val selection = mContextSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mRequestFinalizer.tokenizerFor(provider),
            promptBudget = promptBudget,
        )
        // 组装必需的系统提示词与正文上下文草稿
        val drafts = buildList {
            addRequired(
                content = renderStoryInstructionTemplate(
                    template = AppModel.storySummarizePrompt.replace(
                        oldValue = "{{words}}",
                        newValue = AppModel.summaryWordsLimit.toString(),
                        ignoreCase = true,
                    ),
                    userName = userName,
                    primaryCharacterName = primaryCharacterName,
                ),
                sourceKind = PromptSourceKind.StoryMainPrompt,
            )
            addRequired(
                content = renderStoryMemoryTemplate(AppModel.storyMemoryTemplate, memory),
                sourceKind = PromptSourceKind.StoryMemory,
            )
            addRequired(
                content = renderStorySummaryTemplate(
                    template = AppModel.storySummaryTemplate,
                    summary = currentSummary,
                ),
                sourceKind = PromptSourceKind.StorySummary,
            )
            selection.chunks.forEachIndexed { index, chunk ->
                add(
                    element = PromptMessageDraft(
                        role = LLMMessageRole.User,
                        content = chunk.content,
                        source = PromptSource(
                            kind = PromptSourceKind.StoryDocumentContext,
                            detail = "${chunk.start}-${chunk.end}",
                            referenceId = index.toLong(),
                        ),
                        retentionPriority = PRIORITY_DOCUMENT_MAX -
                            chunk.distance.coerceAtMost(PRIORITY_DOCUMENT_DISTANCE_LIMIT),
                        canDrop = !chunk.required,
                    ),
                )
            }
        }
        // 最终化请求构建与 Token 收敛
        return mRequestFinalizer.finalize(
            drafts = drafts,
            provider = provider,
            model = provider.model,
            options = LLMGenerationOptions(
                temperature = provider.temperature,
                maxTokens = responseTokens,
                topP = provider.topP,
            ),
            includeReasoningInContent = false,
            maxContextTokens = provider.contextTokens,
            maxResponseTokens = responseTokens,
            postProcessingMode = PromptPostProcessingMode.fromOrdinal(provider.promptPostProcessingMode),
            strictPromptPlaceholder = DEFAULT_STRICT_PROMPT_PLACEHOLDER,
        ).request
    }

    /** 辅助函数：向草稿列表追加必需的不可丢弃 System 消息。 */
    private fun MutableList<PromptMessageDraft>.addRequired(
        content: String,
        sourceKind: PromptSourceKind,
    ) {
        if (content.isBlank()) return
        add(
            element = PromptMessageDraft(
                role = LLMMessageRole.System,
                content = content,
                source = PromptSource(sourceKind),
                retentionPriority = PRIORITY_REQUIRED,
                canDrop = false,
            ),
        )
    }

    private companion object {
        /** 故事正文上下文最高保留优先级。 */
        const val PRIORITY_DOCUMENT_MAX = 2_000
        /** 正文距离惩罚上限。 */
        const val PRIORITY_DOCUMENT_DISTANCE_LIMIT = 1_500
        /** 必需消息的最高锁定优先级。 */
        const val PRIORITY_REQUIRED = 10_000
    }
}
