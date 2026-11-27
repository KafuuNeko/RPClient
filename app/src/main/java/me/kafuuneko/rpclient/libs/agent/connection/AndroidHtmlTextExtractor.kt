package me.kafuuneko.rpclient.libs.agent.connection

import android.text.Html

/** Android 装配层的 HTML 实体与段落渲染实现，不向领域工具传播平台依赖。 */
class AndroidHtmlTextExtractor : HtmlTextExtractor {
    private val mExtractor =
        SanitizingHtmlTextExtractor { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }

    /** @return Android 文本渲染并净化后的网页正文。 */
    override fun extract(rawHtml: String): String = mExtractor.extract(rawHtml)
}
