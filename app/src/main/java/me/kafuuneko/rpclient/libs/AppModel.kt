package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val EMAIL = "kafuuneko@gmail.com"
    const val DEFAULT_MAIN_PROMPT = """
Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.
Write one reply only. Do not decide what {{user}} says or does.
"""
    const val DEFAULT_SUMMARIZE_PROMPT = """
Please summarize the following chat history into concise story memory.
Rules:
- Do not continue roleplay.
- Do not generate a new reply for {{char}} or {{user}}.
- Only summarize what already happened in the chat.
- Preserve important facts, relationship changes, promises, injuries, locations, goals, unresolved conflicts and current scene state.
- Keep it within {{words}} words.

Existing summary:
{{summary}}

Chat history to summarize:
{{history}}
"""
    const val DEFAULT_POST_HISTORY_INSTRUCTIONS = ""

    // 当前选中的模型
    var currentLLMProvider by longPref()

    // 主要提示词（Main Prompt）
    var mainPrompt by stringPref(default = DEFAULT_MAIN_PROMPT)

    // 总结提示词（Summarize Prompt）
    var summarizePrompt by stringPref(default = DEFAULT_SUMMARIZE_PROMPT)

    // 全局后置提示词，追加在聊天历史之后；角色卡可通过 {{original}} 覆盖或继承该内容。
    var postHistoryInstructions by stringPref(default = DEFAULT_POST_HISTORY_INSTRUCTIONS)

    // 是否启用流式响应
    var streamEnabled by booleanPref(default = true)

    // Prompt 宏中的用户名称
    var userName by stringPref(default = "You")

    // 总结字数限制
    var summaryWordsLimit by intPref(default = 500)

    // 是否启用自动总结
    var autoSummaryEnabled by booleanPref(default = false)

    // 自动总结触发消息数
    var summaryTriggerMessageCount by intPref(default = 20)

    // 单次总结最多纳入多少条未总结消息，0 表示按上下文预算自动裁剪
    var summaryMaxMessagesPerRequest by intPref(default = 0)

    // 总结请求的最大输出 token
    var summaryResponseTokens by intPref(default = 800)

    // 世界书占 prompt 预算的百分比
    var worldInfoBudgetPercent by intPref(default = 25)

    // 是否启用调试模式
    var debugModeEnabled by booleanPref(default = false)

}
