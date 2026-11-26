package me.kafuuneko.rpclient.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 显式构造 JSON 对象，避免协议与本机快照依赖 Gson 反射。
 * - Kotlin null 表示省略字段，显式 JSON null 使用 JsonNull。
 * - 接受的值仅为 JSON 树、字符串、数值及布尔，禁止隐式序列化业务对象。
 */
fun jsonObject(vararg properties: Pair<String, Any?>): JsonObject = JsonObject().apply {
    properties.forEach { (key, value) ->
        when (value) {
            is JsonElement -> add(key, value.deepCopy())
            is String -> addProperty(key, value)
            is Number -> addProperty(key, value)
            is Boolean -> addProperty(key, value)
            null -> Unit
            else -> error("Unsupported JSON property value")
        }
    }
}

/** 从不可变内容构造有序 JSON 数组，复制节点以免后续请求改写原始快照。 */
fun jsonArray(values: Iterable<JsonElement>): JsonArray =
    JsonArray().apply { values.forEach { add(it.deepCopy()) } }
