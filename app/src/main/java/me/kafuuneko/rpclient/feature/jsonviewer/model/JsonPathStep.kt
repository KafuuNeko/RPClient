package me.kafuuneko.rpclient.feature.jsonviewer.model

/** JSON 导航路径栈单步描述（包含标签、对象 Key 或数组索引）。 */
internal data class JsonPathStep(
    val label: String,
    val objectKey: String?,
    val arrayIndex: Int?
)
