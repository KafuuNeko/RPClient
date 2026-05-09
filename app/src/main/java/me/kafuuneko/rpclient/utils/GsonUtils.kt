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
