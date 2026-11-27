package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 有界 JSON Pointer 读取和对象赋值工具。
 * - 保留缺失节点与显式 null 的区别，限制路径深度和数组索引。
 * - 只处理结构化路径，不解释代码、宏或字符串模板。
 */
object ToolJsonPointer {
    /**
     * 解析 RFC 6901 转义，空字符串表示根节点。
     *
     * @param pointer 经过 RFC 6901 转义的路径。
     * @return 解码后的有界路径段。
     */
    fun segments(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        require(pointer.startsWith('/') && pointer.length <= 1024) { "Invalid JSON pointer" }
        val parts = pointer.drop(1).split('/')
        require(parts.size <= 8) { "JSON pointer is too deep" }
        return parts.map { part ->
            require(!Regex("~(?![01])").containsMatchIn(part)) { "Invalid JSON pointer escape" }
            part.replace("~1", "/").replace("~0", "~")
        }
    }

    /**
     * 缺少路径返回 null；显式 JSON null 仍作为实际值保留。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @param pointer 经过 RFC 6901 转义的路径。
     * @return 目标值；路径缺失时为空，显式 JSON null 保持原样。
     */
    fun read(value: JsonElement, pointer: String): JsonElement? {
        var current = value
        for (segment in segments(pointer)) {
            current = when {
                current.isJsonObject -> current.asJsonObject[segment]
                current.isJsonArray -> segment.toIntOrNull()
                    ?.takeIf { it >= 0 && it < current.asJsonArray.size() }
                    ?.let { current.asJsonArray[it] }

                else -> null
            } ?: return null
        }
        return current
    }

    /**
     * 仅创建对象路径，已有数组允许按有效下标替换，不隐式扩容。
     *
     * @param target 待修改的固定 JSON 请求模板。
     * @param pointer 经过 RFC 6901 转义的路径。
     * @param value 经过领域校验的配置或 JSON 值。
     */
    fun assign(target: JsonObject, pointer: String, value: JsonElement) {
        val parts = segments(pointer)
        require(parts.isNotEmpty()) { "Cannot replace JSON root" }
        var current: JsonElement = target
        // 固定模板中的结构决定赋值方式，模型只能提供叶子值。
        parts.dropLast(1).forEach { name ->
            if (current.isJsonObject && !current.asJsonObject.has(name)) current.asJsonObject.add(
                name,
                JsonObject()
            )
            current = read(current, "/" + name.replace("~", "~0").replace("/", "~1"))
                ?: throw IllegalArgumentException("Missing JSON binding path")
        }
        val name = parts.last()
        when {
            current.isJsonObject -> current.asJsonObject.add(name, value.deepCopy())
            current.isJsonArray -> {
                val index = name.toIntOrNull()
                require(index != null && index >= 0 && index < current.asJsonArray.size()) { "Invalid JSON binding index" }
                current.asJsonArray.set(index, value.deepCopy())
            }

            else -> error("Invalid JSON binding parent")
        }
    }
}
