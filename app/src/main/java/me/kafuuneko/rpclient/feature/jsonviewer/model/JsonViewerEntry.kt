package me.kafuuneko.rpclient.feature.jsonviewer.model

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
