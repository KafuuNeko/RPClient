package me.kafuuneko.rpclient.feature.jsonviewer.model

import me.kafuuneko.rpclient.utils.toPreview
import me.kafuuneko.rpclient.libs.json.splitJsonText
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 使用 JSONTokener 安全解析 JSON 字符串为 JSONObject、JSONArray 或原始基本类型。 */
internal fun parseJson(json: String): Result<Any?> {
    return try {
        val tokener = JSONTokener(json)
        val value = tokener.nextValue()
        val next = tokener.nextClean()
        if (next.code != 0) error("Unexpected data after JSON value.")
        Result.success(value)
    } catch (error: Exception) {
        Result.failure(error)
    }
}

/** 将当前聚焦的 JSONObject 或 JSONArray 映射为子条目列表。 */
internal fun Any?.toEntries(limit: Int = 100): List<JsonViewerEntry> {
    // 对象沿用解析器的键迭代顺序，数组沿用原索引，确保导航定位一致。
    return when (this) {
        is JSONObject -> keys().asSequence().take(limit).mapIndexed { index, key ->
            val value = opt(key)
            value.toEntry(
                id = index,
                name = key,
                sourceKey = key,
                sourceIndex = null,
            )
        }.toList()

        is JSONArray -> (0 until minOf(length(), limit)).map { index ->
            val value = opt(index)
            value.toEntry(
                id = index,
                name = "[$index]",
                sourceKey = null,
                sourceIndex = index,
            )
        }

        else -> listOf(toEntry(id = 0, name = "$", sourceKey = null, sourceIndex = null))
    }
}

/** 将单个子值封装为列表项视图模型。 */
private fun Any?.toEntry(
    id: Int,
    name: String,
    sourceKey: String?,
    sourceIndex: Int?,
): JsonViewerEntry {
    // 只复制允许展示的值，源 key 继续用于导航，避免标签缩略影响路径定位。
    val text = valuePreview()
    return JsonViewerEntry(
        id = id,
        name = if (name.length > 256) safePrefix(name, 256) + "…" else name,
        type = nodeType(),
        preview = if (this is JSONObject || this is JSONArray) text else "",
        childCount = childCount(),
        sourceKey = sourceKey,
        sourceIndex = sourceIndex,
        valueBlocks = splitJsonText(text),
        originalLength = if (this is String) length else 0,
        isTruncated = this is String && length > VALUE_PREVIEW_LENGTH,
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
        is String -> if (length > VALUE_PREVIEW_LENGTH) safePrefix(this, 4096) else this
        is Boolean, is Number -> toString()
        JSONObject.NULL, null -> "null"
        else -> toString().toPreview(VALUE_PREVIEW_LENGTH)
    }
}

private const val VALUE_PREVIEW_LENGTH = 100_000

/** 超长字段的前缀同样避开代理对边界。 */
private fun safePrefix(value: String, length: Int): String {
    val end =
        if (value[length - 1].isHighSurrogate() && value[length].isLowSurrogate()) length - 1 else length
    return value.substring(0, end)
}
