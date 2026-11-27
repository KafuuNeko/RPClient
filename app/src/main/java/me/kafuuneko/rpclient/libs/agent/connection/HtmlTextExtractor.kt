package me.kafuuneko.rpclient.libs.agent.connection

/** 平台无关的网页正文提取边界，JVM 回归可注入文本渲染实现。 */
fun interface HtmlTextExtractor {
    /** @return 去除非正文节点与无意义空白后的可读文本。 */
    fun extract(rawHtml: String): String
}

/** 先清除脚本及非正文节点，再调用平台实体解码；纯逻辑可在 JVM 验证。 */
class SanitizingHtmlTextExtractor(private val mRender: (String) -> String) : HtmlTextExtractor {
    /** @return 有序净化后的正文，不保留图片对象占位符。 */
    override fun extract(rawHtml: String): String {
        val stripped = rawHtml.replace(mIgnoredBlocks, "")
        return mRender(stripped)
            .replace("\uFFFC", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(mExcessiveSpaces, " ")
            .replace(mExcessiveNewlines, "\n\n")
            .trim()
    }

    private companion object {
        val mIgnoredBlocks = Regex(
            "<(script|style|head|noscript|svg|iframe|template)\\b[^>]*>.*?(?:</\\1\\s*>|\\z)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val mExcessiveSpaces = Regex("[ \\t\\x0B\\f]{2,}")
        val mExcessiveNewlines = Regex("\\n{3,}")
    }
}
