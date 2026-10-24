package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript

/**
 * 流式生成初始化时绑定的正则脚本与宏快照。
 */
internal data class StreamingRegexContext(
    /** 当前页面或流程可使用的正则脚本列表。 */
    val scripts: List<ScopedRegexScript> = emptyList(),
    /** 当前正则执行允许展开的宏变量映射。 */
    val macros: Map<String, String> = emptyMap()
)
