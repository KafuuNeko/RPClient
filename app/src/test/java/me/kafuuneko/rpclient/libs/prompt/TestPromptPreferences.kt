package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition

/** JVM 测试显式提供的配置；引用编译期常量，不初始化 Android 偏好存储。 */
internal class TestPromptPreferences(
    override val mainPrompt: String = PromptType.Main.defaultValue,
    override val postHistoryInstructions: String = PromptType.PostHistory.defaultValue,
    override val auxiliaryPrompt: String = PromptType.Auxiliary.defaultValue,
    override val impersonationPrompt: String = PromptType.Impersonation.defaultValue,
    override val newChatPrompt: String = PromptType.NewChat.defaultValue,
    override val newExampleChatPrompt: String = PromptType.NewExampleChat.defaultValue,
    override val continueNudgePrompt: String = PromptType.ContinueNudge.defaultValue,
    override val groupNudgePrompt: String = PromptType.GroupNudge.defaultValue,
    override val newGroupChatPrompt: String = PromptType.NewGroupChat.defaultValue,
    override val worldInfoFormat: String = PromptType.WorldInfoFormat.defaultValue,
    override val scenarioFormat: String = PromptType.ScenarioFormat.defaultValue,
    override val personalityFormat: String = PromptType.PersonalityFormat.defaultValue,
    override val userPersonaFormat: String = PromptType.UserPersonaFormat.defaultValue,
    override val summaryInjectionTemplate: String = PromptType.SummaryInjection.defaultValue,
    override val summaryInjectionPosition: Int = SummaryInjectionPosition.default.persistedValue,
    override val summaryInjectionDepth: Int = 2,
    override val summaryInjectionRole: Int = 0,
    override val worldInfoBudgetPercent: Int = 25,
    override val worldInfoBudgetCap: Int = 0,
    override val includeThinkInContext: Boolean = false
) : PromptPreferences
