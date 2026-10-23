package me.kafuuneko.rpclient.utils

import com.google.gson.Gson

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
 */
fun formatJsonPretty(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val jsonElement = com.google.gson.JsonParser.parseString(trimmed)
        val gson = com
            .google
            .gson
            .GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
        gson.toJson(jsonElement)
    }.getOrDefault(trimmed)
}


