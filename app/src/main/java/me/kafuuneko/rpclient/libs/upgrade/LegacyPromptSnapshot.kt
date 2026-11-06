package me.kafuuneko.rpclient.libs.upgrade

import me.kafuuneko.rpclient.libs.prompt.PromptType

/** 旧偏好的一次性快照，只保留实际保存且不同于枚举默认值的项。 */
data class LegacyPromptSnapshot(val hasSavedValues: Boolean, val values: Map<String, String>) {
    companion object {
        /** 冻结旧偏好键，枚举名称调整时仍需保持映射兼容。 */
        private val fields = linkedMapOf(
            "mainPrompt" to PromptType.Main,
            "auxiliaryPrompt" to PromptType.Auxiliary,
            "postHistoryInstructions" to PromptType.PostHistory,
            "summarizePrompt" to PromptType.Summarize,
            "summaryInjectionTemplate" to PromptType.SummaryInjection,
            "impersonationPrompt" to PromptType.Impersonation,
            "newChatPrompt" to PromptType.NewChat,
            "newExampleChatPrompt" to PromptType.NewExampleChat,
            "continueNudgePrompt" to PromptType.ContinueNudge,
            "replaceEmptyMessagePrompt" to PromptType.ReplaceEmptyMessage,
            "worldInfoFormat" to PromptType.WorldInfoFormat,
            "scenarioFormat" to PromptType.ScenarioFormat,
            "personalityFormat" to PromptType.PersonalityFormat,
            "userPersonaFormat" to PromptType.UserPersonaFormat,
            "groupNudgePrompt" to PromptType.GroupNudge,
            "newGroupChatPrompt" to PromptType.NewGroupChat,
            "groupSummarizePrompt" to PromptType.GroupSummarize,
            "storyMainPrompt" to PromptType.StoryMain,
            "storyMemoryTemplate" to PromptType.StoryMemory,
            "storySummaryTemplate" to PromptType.StorySummary,
            "storySummarizePrompt" to PromptType.StorySummarize,
            "storyContinuationGuidancePrompt" to PromptType.StoryContinuationGuidance,
            "storyContinuePrompt" to PromptType.StoryContinue,
        )

        /** 清理只涉及旧提示词键，不包含其他设置。 */
        val keys: Set<String> get() = fields.keys

        /** 验证存在的值是字符串；缺键与默认值均不写覆盖行。 */
        fun read(preferences: Map<String, *>): LegacyPromptSnapshot {
            val values = mutableMapOf<String, String>()
            fields.forEach { (oldKey, type) ->
                if (preferences.containsKey(oldKey)) {
                    val value =
                        requireNotNull(preferences[oldKey] as? String) { "Legacy prompt preference is not a string" }
                    if (value != type.defaultValue) values[type.key] = value
                }
            }
            return LegacyPromptSnapshot(fields.keys.any(preferences::containsKey), values)
        }
    }
}
