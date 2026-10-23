package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException

/** 将目录响应限制为 JSON 对象，并把解析细节统一折叠为可展示的目录响应错误。 */
internal fun parseCatalogJsonObject(raw: String): JsonObject {
    return runCatching { JsonParser.parseString(raw).asJsonObject }
        .getOrElse { throw LLMModelCatalogInvalidResponseException() }
}

/** 容忍兼容网关的非字符串标量，但把空白和 JSON null 统一视为缺失。 */
internal fun JsonObject.stringOrNull(name: String): String? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return runCatching { element.asString.trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

/** 读取可信的正整数限制；零、负数和类型错误均视为模型服务未声明限制。 */
internal fun JsonObject.positiveIntOrNull(name: String): Int? {
    val element = get(name)?.takeUnless { it.isJsonNull } ?: return null
    return runCatching { element.asInt }
        .getOrNull()
        ?.takeIf { it > 0 }
}

/** 仅在字段确实为数组时返回值，避免 Gson 的类型访问异常向上泄露。 */
internal fun JsonObject.arrayOrNull(name: String): JsonArray? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonArray }?.asJsonArray
}

/** 仅在字段确实为对象时返回值，用于读取可选的模型服务扩展结构。 */
internal fun JsonObject.objectOrNull(name: String): JsonObject? {
    val element = get(name) ?: return null
    return element.takeIf { it.isJsonObject }?.asJsonObject
}

/** 将异构数组中的有效非空字符串去重，并保持服务端原始顺序。 */
internal fun JsonArray.toStringSet(): Set<String> {
    return mapNotNullTo(linkedSetOf()) { element ->
        element
            .takeIf { element -> element.isJsonPrimitive && element.asJsonPrimitive.isString }
            ?.asString
            ?.trim()
            ?.takeIf { text -> text.isNotBlank() }
    }
}

/** 在忽略非字符串成员的前提下检查能力名称。 */
internal fun JsonArray.containsString(value: String): Boolean {
    return any { element ->
        element.isJsonPrimitive
            && element.asJsonPrimitive.isString
            && element.asString == value
    }
}
