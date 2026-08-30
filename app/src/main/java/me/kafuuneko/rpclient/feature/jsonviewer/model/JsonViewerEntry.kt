package me.kafuuneko.rpclient.feature.jsonviewer.model

/** JSON 当前层级中的一项，sourceKey/sourceIndex 用于定位原始父节点。 */
data class JsonViewerEntry(
    /** 当前记录或列表项的唯一标识。 */
    val id: Int,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前对象所属的业务类型。 */
    val type: JsonViewerNodeType,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 当前 JSON 节点或结构项包含的直接子项数量。 */
    val childCount: Int,
    /** 当前 JSON 子节点在父对象中的字段名。 */
    val sourceKey: String? = null,
    /** 当前 JSON 子节点在父数组中的位置。 */
    val sourceIndex: Int? = null
) {
    val hasChildren: Boolean
        get() = type == JsonViewerNodeType.Object || type == JsonViewerNodeType.Array
}
