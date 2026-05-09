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

}