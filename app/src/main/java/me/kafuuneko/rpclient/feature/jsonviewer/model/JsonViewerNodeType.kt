package me.kafuuneko.rpclient.feature.jsonviewer.model

/** JSON 节点类型，用于选择预览文本和是否允许继续下钻。 */
enum class JsonViewerNodeType {
    Object,
    Array,
    String,
    Number,
    Boolean,
    Null
}
