package me.kafuuneko.rpclient.libs.agent.model

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader
import java.security.MessageDigest

/**
 * 工具与检查点共用的有界 JSON 边界。
 * - 在构建树前限制深度并拒绝重名键，避免参数被不同协议解释为不同内容。
 * - 规范化对象键用于内容指纹；数组顺序始终保留。
 */
object AgentJson {
    /** 严格解析单个 JSON 值，不接受尾随内容、重复键及非有限数值。 */
    fun parse(raw: String, maxBytes: Int = 64 * 1024, maxDepth: Int = 8): JsonElement {
        require(raw.toByteArray(Charsets.UTF_8).size <= maxBytes) { "JSON payload is too large" }
        var nodes = 0
        JsonReader(StringReader(raw)).use { reader ->
            reader.strictness = Strictness.STRICT
            fun read(depth: Int): JsonElement {
                require(depth <= maxDepth && ++nodes <= 100_000) { "JSON payload is too complex" }
                return when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        val result = JsonObject()
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val name = reader.nextName()
                            require(!result.has(name)) { "Duplicate JSON property" }
                            result.add(name, read(depth + 1))
                        }
                        reader.endObject()
                        result
                    }

                    JsonToken.BEGIN_ARRAY -> {
                        val result = JsonArray()
                        reader.beginArray()
                        while (reader.hasNext()) result.add(read(depth + 1))
                        reader.endArray()
                        result
                    }

                    JsonToken.STRING -> JsonPrimitive(reader.nextString())
                    JsonToken.NUMBER -> {
                        val number = reader.nextString()
                        require(number.length <= 128) { "JSON number is too long" }
                        JsonPrimitive(number.toBigDecimal())
                    }

                    JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                    JsonToken.NULL -> {
                        reader.nextNull(); JsonNull.INSTANCE
                    }

                    else -> error("Invalid JSON token")
                }
            }
            // 深度限制在递归入口，不能先使用无界树解析后才检查。
            val result = read(1)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing JSON content" }
            return result
        }
    }

    /** 对象属性顺序不影响幂等指纹，缺失字段与显式 null 保持不同。 */
    fun canonical(value: JsonElement): String = when {
        value.isJsonObject -> value.asJsonObject.entrySet().sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}"
            ) { (key, child) -> "${JsonPrimitive(key)}:${canonical(child)}" }

        value.isJsonArray -> value.asJsonArray.joinToString(
            prefix = "[",
            postfix = "]"
        ) { canonical(it) }

        else -> value.toString()
    }

    /** 生成不可逆内容标识，日志及数据库无需保存原始参数。 */
    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
