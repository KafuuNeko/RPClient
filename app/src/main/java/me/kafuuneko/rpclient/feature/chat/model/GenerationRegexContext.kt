package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript

/**
 * 在 Prompt 构建时冻结的正则脚本和人设宏，响应落库和流式预览共用。
 */
internal data class GenerationRegexContext(
    /** 当前页面或流程可使用的正则脚本列表。 */
    val scripts: List<ScopedRegexScript>,
    /** 当前正则执行允许展开的宏变量映射。 */
    val macros: Map<String, String>
)
