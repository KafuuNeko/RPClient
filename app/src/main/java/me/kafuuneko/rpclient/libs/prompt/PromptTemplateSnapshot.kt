package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition

/** 一次生成共享的不可变模板与构建偏好，禁止在构建中重新查询当前模板。 */
data class PromptTemplateSnapshot(
    val presetId: Long,
    val revision: Long,
    val values: Map<PromptType, String>,
    override val summaryInjectionPosition: Int = SummaryInjectionPosition.default.persistedValue,
    override val summaryInjectionDepth: Int = 2,
    override val summaryInjectionRole: Int = 0,
    override val worldInfoBudgetPercent: Int = 25,
    override val worldInfoBudgetCap: Int = 0,
    override val includeThinkInContext: Boolean = false,
) : PromptPreferences {
    override val mainPrompt: String get() = values.getValue(PromptType.Main)
    override val auxiliaryPrompt: String get() = values.getValue(PromptType.Auxiliary)
    override val postHistoryInstructions: String get() = values.getValue(PromptType.PostHistory)
    val summarizePrompt: String get() = values.getValue(PromptType.Summarize)
    override val summaryInjectionTemplate: String get() = values.getValue(PromptType.SummaryInjection)
    override val impersonationPrompt: String get() = values.getValue(PromptType.Impersonation)
    override val userWritingPrompt: String get() = values.getValue(PromptType.UserWriting)
    override val newChatPrompt: String get() = values.getValue(PromptType.NewChat)
    override val newExampleChatPrompt: String get() = values.getValue(PromptType.NewExampleChat)
    override val continueNudgePrompt: String get() = values.getValue(PromptType.ContinueNudge)
    val replaceEmptyMessagePrompt: String get() = values.getValue(PromptType.ReplaceEmptyMessage)
    override val worldInfoFormat: String get() = values.getValue(PromptType.WorldInfoFormat)
    override val scenarioFormat: String get() = values.getValue(PromptType.ScenarioFormat)
    override val personalityFormat: String get() = values.getValue(PromptType.PersonalityFormat)
    override val userPersonaFormat: String get() = values.getValue(PromptType.UserPersonaFormat)
    override val groupNudgePrompt: String get() = values.getValue(PromptType.GroupNudge)
    override val groupImpersonationPrompt: String get() = values.getValue(PromptType.GroupImpersonation)
    override val groupUserWritingPrompt: String get() = values.getValue(PromptType.GroupUserWriting)
    override val newGroupChatPrompt: String get() = values.getValue(PromptType.NewGroupChat)
    val groupSummarizePrompt: String get() = values.getValue(PromptType.GroupSummarize)
    val storyMainPrompt: String get() = values.getValue(PromptType.StoryMain)
    val storyMemoryTemplate: String get() = values.getValue(PromptType.StoryMemory)
    val storySummaryTemplate: String get() = values.getValue(PromptType.StorySummary)
    val storySummarizePrompt: String get() = values.getValue(PromptType.StorySummarize)
    val storyContinuationGuidancePrompt: String get() = values.getValue(PromptType.StoryContinuationGuidance)
    val storyContinuePrompt: String get() = values.getValue(PromptType.StoryContinue)

    companion object {
        const val BUILTIN_ID = -1L
        const val LEGACY_ID = 1L

        /** 将数据库中的覆盖值展开为完整快照，显式空串不使用默认值替代。 */
        fun resolve(
            presetId: Long,
            revision: Long,
            overrides: Map<String, String>
        ): PromptTemplateSnapshot {
            return PromptTemplateSnapshot(
                presetId = presetId,
                revision = revision,
                values = PromptType.entries.associateWith { type ->
                    overrides[type.key] ?: type.defaultValue
                },
            )
        }

        /** 提供不依赖 Android 存储的内置快照，供新建模板和纯构建测试使用。 */
        fun defaults(): PromptTemplateSnapshot = resolve(BUILTIN_ID, 1, emptyMap())
    }
}
