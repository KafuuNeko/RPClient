package me.kafuuneko.rpclient.feature.jsonviewer.model

/** JSON 当前层级中的一项，sourceKey/sourceIndex 用于定位原始父节点。 */
data class JsonViewerEntry(
    val id: Int,
    val name: String,
    val type: JsonViewerNodeType,
    val preview: String,
    val childCount: Int,
    val sourceKey: String? = null,
    val sourceIndex: Int? = null
) {
    val hasChildren: Boolean
        get() = type == JsonViewerNodeType.Object || type == JsonViewerNodeType.Array
}
