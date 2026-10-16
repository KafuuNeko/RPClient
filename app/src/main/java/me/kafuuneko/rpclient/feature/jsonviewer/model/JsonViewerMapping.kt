package me.kafuuneko.rpclient.feature.jsonviewer.model

import me.kafuuneko.rpclient.utils.toPreview
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 使用 JSONTokener 安全解析 JSON 字符串为 JSONObject、JSONArray 或原始基本类型。 */
internal fun parseJson(json: String): Result<Any?> {
    return runCatching {
        val tokener = JSONTokener(json)
        val value = tokener.nextValue()
        val next = tokener.nextClean()
        if (next.code != 0) error("Unexpected data after JSON value.")
        value
    }
}

/** 将当前聚焦的 JSONObject 或 JSONArray 映射为子条目列表。 */
internal fun Any?.toEntries(): List<JsonViewerEntry> {
    // 对象沿用解析器的键迭代顺序，数组沿用原索引，确保导航定位一致。
    return when (this) {
        is JSONObject -> keys().asSequence().toList().mapIndexed { index, key ->
            val value = opt(key)
            value.toEntry(
                id = index,
                name = key,
                sourceKey = key,
                sourceIndex = null
            )
        }

        is JSONArray -> (0 until length()).map { index ->
            val value = opt(index)
            value.toEntry(
                id = index,
                name = "[$index]",
                sourceKey = null,
                sourceIndex = index
            )
        }

        else -> emptyList()
    }
}

/** 将单个子值封装为列表项视图模型。 */
private fun Any?.toEntry(
    id: Int,
    name: String,
    sourceKey: String?,
    sourceIndex: Int?
): JsonViewerEntry {
    return JsonViewerEntry(
        id = id,
        name = name,
        type = nodeType(),
        preview = valuePreview(),
        childCount = childCount(),
        sourceKey = sourceKey,
        sourceIndex = sourceIndex
    )
}

/** 推断 JSON 节点类型枚举。 */
internal fun Any?.nodeType(): JsonViewerNodeType {
    return when (this) {
        is JSONObject -> JsonViewerNodeType.Object
        is JSONArray -> JsonViewerNodeType.Array
        is Boolean -> JsonViewerNodeType.Boolean
        is Number -> JsonViewerNodeType.Number
        JSONObject.NULL, null -> JsonViewerNodeType.Null
        else -> JsonViewerNodeType.String
    }
}

/** 计算节点的直接子元素数量。 */
internal fun Any?.childCount(): Int {
    return when (this) {
        is JSONObject -> length()
        is JSONArray -> length()
        else -> 0
    }
}

/** 生成节点值的短文本摘要预览。 */
private fun Any?.valuePreview(): String {
    return when (this) {
        is JSONObject -> "{…}"
        is JSONArray -> "[…]"
        is String -> toPreview(VALUE_PREVIEW_LENGTH)
        is Boolean, is Number -> toString()
        JSONObject.NULL, null -> "null"
        else -> toString().toPreview(VALUE_PREVIEW_LENGTH)
    }
}

private const val VALUE_PREVIEW_LENGTH = 180
