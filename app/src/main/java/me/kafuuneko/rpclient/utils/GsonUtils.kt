package me.kafuuneko.rpclient.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser

private val prettyGson =
    GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create()

/**
 * 格式化已解析的 JSON，保留对象中用于删除请求字段的显式 null。
 * @return 供编辑器展示和保存的 JSON。
 */
internal fun JsonElement.toPrettyJson(): String = prettyGson.toJson(this)

/**
 * 将 JSON 字符串解析为字符串列表。
 * 如果解析失败或字符串为空，则返回空列表。
 */
fun Gson.toStringList(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val array = this.fromJson(json, Array<String>::class.java)
        array?.toList() ?: emptyList()
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

/**
 * 将字符串列表序列化为 JSON 字符串。
 */
fun Gson.toJsonString(list: List<String>?): String {
    if (list == null) return "[]"
    return this.toJson(list)
}

/**
 * 将 JSON 字符串格式化为 2 空格缩进的 Pretty 结构。
 * 如果解析失败或字符串为空，则返回原始去除首尾空白的字符串。
 * @param raw 待格式化的 JSON 原文。
 * @return 保留显式空值的格式化文本或原文。
 */
fun formatJsonPretty(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        JsonParser.parseString(trimmed).toPrettyJson()
    }.getOrDefault(trimmed)
}
