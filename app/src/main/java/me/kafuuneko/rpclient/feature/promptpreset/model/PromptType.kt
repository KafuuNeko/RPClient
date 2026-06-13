package me.kafuuneko.rpclient.feature.promptpreset.model

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
    Impersonation,
    NewChat,
    NewExampleChat,
    ContinueNudge,
    ReplaceEmptyMessage,
    WorldInfoFormat,
    ScenarioFormat,
    PersonalityFormat,
    GroupNudge,
    NewGroupChat,
    GroupSummarize
}
