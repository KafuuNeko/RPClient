package me.kafuuneko.rpclient.libs.prompt

/**
 * 单聊与群聊构建器使用的只读偏好。
 *
 * 生产环境使用 Repository 捕获的不可变模板快照；测试显式传入配置，不依赖 Android 存储。
 */
interface PromptPreferences {
    /** 全局主提示词。 */
    val mainPrompt: String

    /** 历史后指令。 */
    val postHistoryInstructions: String

    /** 辅助提示词。 */
    val auxiliaryPrompt: String

    /** 单聊扮演用户提示词。 */
    val impersonationPrompt: String

    /** 单聊扮演用户时的输入框续写模板，使用 {{draft}} 放置未发送文字。 */
    val userWritingPrompt: String

    /** 新聊天边界标记。 */
    val newChatPrompt: String

    /** 示例聊天边界标记。 */
    val newExampleChatPrompt: String

    /** 续写引导提示词。 */
    val continueNudgePrompt: String

    /** 群聊发言约束。 */
    val groupNudgePrompt: String

    /** 群聊扮演用户的身份与群成员约束。 */
    val groupImpersonationPrompt: String

    /** 群聊输入框续写模板，使用 {{draft}} 放置未发送文字。 */
    val groupUserWritingPrompt: String

    /** 群聊边界标记。 */
    val newGroupChatPrompt: String

    /** 世界书内容包装模板。 */
    val worldInfoFormat: String

    /** 场景包装模板。 */
    val scenarioFormat: String

    /** 性格包装模板。 */
    val personalityFormat: String

    /** 用户人设包装模板。 */
    val userPersonaFormat: String

    /** 摘要包装模板。 */
    val summaryInjectionTemplate: String

    /** 摘要注入位置的存储值。 */
    val summaryInjectionPosition: Int

    /** 摘要注入深度。 */
    val summaryInjectionDepth: Int

    /** 摘要消息角色的存储值。 */
    val summaryInjectionRole: Int

    /** 世界书占可用 Prompt 预算的百分比。 */
    val worldInfoBudgetPercent: Int

    /** 世界书绝对 Token 上限，0 表示不限制。 */
    val worldInfoBudgetCap: Int

    /** 是否将已保存的推理块带回上下文。 */
    val includeThinkInContext: Boolean
}
