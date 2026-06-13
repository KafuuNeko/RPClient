package me.kafuuneko.rpclient.feature.jsonviewer.model

/** 跨 Activity 临时传递的 JSON 文本及页面标题。 */
data class JsonViewerPayload(
    val title: String,
    val json: String
)
