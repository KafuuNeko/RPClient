package me.kafuuneko.rpclient.libs

import com.chibatching.kotpref.KotprefModel

object AppModel : KotprefModel() {
    const val EMAIL = "kafuuneko@gmail.com"

    // 当前选中的模型
    var currentLLMProvider by longPref()

    // 主要提示词（Main Prompt）
    var mainPrompt by nullableStringPref(default = null)

    // 总结提示词（Summarize Prompt）
    var summarizePrompt by nullableStringPref(default = null)

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
    var worldInfoBudgetPercent by intPref(default = 12)

}
