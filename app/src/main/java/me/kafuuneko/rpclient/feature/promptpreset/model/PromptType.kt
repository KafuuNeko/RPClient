package me.kafuuneko.rpclient.feature.promptpreset.model

import me.kafuuneko.rpclient.libs.AppModel

/**
 * 可由用户覆盖的 Prompt 模板类型。
 *
 * 枚举值由 ViewModel 映射到 AppModel 字段，不应依赖 ordinal 进行持久化。
 */
enum class PromptType {
    Main,
    Auxiliary,
    PostHistory,
    Summarize,
    SummaryInjection,
    Impersonation,
    NewChat,
    NewExampleChat,
    ContinueNudge,
    ReplaceEmptyMessage,
    WorldInfoFormat,
    ScenarioFormat,
    PersonalityFormat,
    UserPersonaFormat,
    GroupNudge,
    NewGroupChat,
    GroupSummarize,
    StoryMain,
    StoryMemory,
    StorySummary,
    StorySummarize,
    StoryContinuationGuidance,
    StoryContinue
}

/** 获取指定提示词类型的系统内置默认模板文本。 */
internal fun defaultPrompt(type: PromptType): String {
    // 默认模板来自固定配置，与用户已经保存的覆盖文本保持分离。
    return when (type) {
        PromptType.Main -> AppModel.DEFAULT_MAIN_PROMPT
        PromptType.Auxiliary -> AppModel.DEFAULT_AUXILIARY_PROMPT
        PromptType.PostHistory -> AppModel.DEFAULT_POST_HISTORY_INSTRUCTIONS
        PromptType.Summarize -> AppModel.DEFAULT_SUMMARIZE_PROMPT
        PromptType.SummaryInjection -> AppModel.DEFAULT_SUMMARY_INJECTION_TEMPLATE
        PromptType.Impersonation -> AppModel.DEFAULT_IMPERSONATION_PROMPT
        PromptType.NewChat -> AppModel.DEFAULT_NEW_CHAT_PROMPT
        PromptType.NewExampleChat -> AppModel.DEFAULT_NEW_EXAMPLE_CHAT_PROMPT
        PromptType.ContinueNudge -> AppModel.DEFAULT_CONTINUE_NUDGE_PROMPT
        PromptType.ReplaceEmptyMessage -> AppModel.DEFAULT_REPLACE_EMPTY_MESSAGE_PROMPT
        PromptType.WorldInfoFormat -> AppModel.DEFAULT_WORLD_INFO_FORMAT
        PromptType.ScenarioFormat -> AppModel.DEFAULT_SCENARIO_FORMAT
        PromptType.PersonalityFormat -> AppModel.DEFAULT_PERSONALITY_FORMAT
        PromptType.UserPersonaFormat -> AppModel.DEFAULT_USER_PERSONA_FORMAT
        PromptType.GroupNudge -> AppModel.DEFAULT_GROUP_NUDGE_PROMPT
        PromptType.NewGroupChat -> AppModel.DEFAULT_NEW_GROUP_CHAT_PROMPT
        PromptType.GroupSummarize -> AppModel.DEFAULT_GROUP_SUMMARIZE_PROMPT
        PromptType.StoryMain -> AppModel.DEFAULT_STORY_MAIN_PROMPT
        PromptType.StoryMemory -> AppModel.DEFAULT_STORY_MEMORY_TEMPLATE
        PromptType.StorySummary -> AppModel.DEFAULT_STORY_SUMMARY_TEMPLATE
        PromptType.StorySummarize -> AppModel.DEFAULT_STORY_SUMMARIZE_PROMPT
        PromptType.StoryContinuationGuidance -> AppModel.DEFAULT_STORY_CONTINUATION_GUIDANCE_PROMPT
        PromptType.StoryContinue -> AppModel.DEFAULT_STORY_CONTINUE_PROMPT
    }
}

/** 获取指定提示词类型支持注入的宏变量标签列表（如 `{{char}}`, `{{user}}` 等）。 */
internal fun availableMacros(type: PromptType): List<String> {
    // 每种模板保留原有宏集合和展示顺序，避免引入不支持的占位符。
    return when (type) {
        PromptType.Main,
        PromptType.Auxiliary,
        PromptType.PostHistory,
        PromptType.Impersonation,
        PromptType.ContinueNudge -> listOf("{{char}}", "{{user}}")
        PromptType.GroupNudge -> listOf("{{char}}", "{{user}}", "{{group}}")
        PromptType.NewGroupChat -> listOf("{{group}}", "{{char}}", "{{user}}")
        PromptType.Summarize,
        PromptType.GroupSummarize,
        PromptType.StorySummarize -> listOf("{{words}}", "{{char}}", "{{user}}")
        PromptType.SummaryInjection,
        PromptType.StorySummary -> listOf("{{summary}}")
        PromptType.StoryMemory -> listOf("{{memory}}")
        PromptType.StoryContinuationGuidance -> listOf("{{guidance}}")
        PromptType.WorldInfoFormat -> listOf("{0}")
        PromptType.ScenarioFormat -> listOf("{{scenario}}")
        PromptType.PersonalityFormat -> listOf("{{personality}}")
        PromptType.UserPersonaFormat -> listOf("{{persona}}", "{{user}}")
        else -> listOf("{{char}}", "{{user}}")
    }
}
