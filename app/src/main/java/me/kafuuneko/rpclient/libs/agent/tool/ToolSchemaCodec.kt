package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson

/** 显式编码 Schema 快照，不依赖 Gson 反射字段名，R8 后仍保持稳定。 */
object ToolSchemaCodec {
    /** 严格读取用户导入的公共子集，未知关键字不能被静默丢弃。 */
    fun decode(raw: String): ToolSchema {
        val value = AgentJson.parse(raw, maxDepth = 32).asJsonObject
        return decodeNode(value, 1).also(ToolSchemaValidator::validateDefinition)
    }

    private fun decodeNode(value: JsonObject, depth: Int): ToolSchema {
        require(depth <= ToolSchemaValidator.MAX_DEPTH) { "Tool schema is too complex" }
        val allowed = setOf(
            "type",
            "description",
            "nullable",
            "properties",
            "required",
            "additionalProperties",
            "items",
            "enum",
            "minimum",
            "maximum",
            "minLength",
            "maxLength",
            "minItems",
            "maxItems"
        )
        require(value.keySet().all { it in allowed }) { "Unsupported tool schema keyword" }
        // 关键字类型必须明确，拒绝把字符串或小数隐式转成布尔值及整数限制。
        listOf("nullable", "additionalProperties").forEach { key ->
            require(!value.has(key) || value[key].asJsonPrimitive.isBoolean) { "Invalid schema boolean" }
        }
        listOf("minLength", "maxLength", "minItems", "maxItems").forEach { key ->
            if (value.has(key)) require(value[key].asBigDecimal.intValueExact() >= 0) { "Invalid schema length" }
        }
        val required = value.getAsJsonArray("required")?.map { it.asString }.orEmpty()
        require(required.distinct().size == required.size) { "Duplicate required property" }
        return ToolSchema(
            type = ToolSchema.Type.entries.first { it.name.lowercase() == value["type"].asString },
            description = value["description"]?.asString.orEmpty(),
            nullable = value["nullable"]?.asBoolean ?: false,
            properties = value.getAsJsonObject("properties")?.entrySet()
                ?.associate { it.key to decodeNode(it.value.asJsonObject, depth + 1) }.orEmpty(),
            required = required.toSet(),
            additionalProperties = value["additionalProperties"]?.asBoolean ?: false,
            items = value.getAsJsonObject("items")?.let { decodeNode(it, depth + 1) },
            enumValues = value.getAsJsonArray("enum")?.map { it.deepCopy() },
            minimum = value["minimum"]?.asDouble,
            maximum = value["maximum"]?.asDouble,
            minLength = value["minLength"]?.asInt,
            maxLength = value["maxLength"]?.asInt,
            minItems = value["minItems"]?.asInt,
            maxItems = value["maxItems"]?.asInt,
        )
    }

    /** 输出通用 JSON Schema 子集；协议特有的能力裁剪由各 ToolProtocolCodec 负责。 */
    fun encode(schema: ToolSchema): JsonObject {
        ToolSchemaValidator.validateDefinition(schema)
        return encodeNode(schema)
    }

    private fun encodeNode(schema: ToolSchema): JsonObject = JsonObject().apply {
        addProperty("type", schema.type.name.lowercase())
        if (schema.description.isNotEmpty()) addProperty("description", schema.description)
        if (schema.nullable) addProperty("nullable", true)
        // 只输出适用类型的限制，缺省值与显式 null 不进行转换。
        if (schema.type == ToolSchema.Type.Object) {
            add("properties", JsonObject().apply {
                schema.properties.forEach { (name, child) -> add(name, encodeNode(child)) }
            })
            add("required", JsonArray().apply { schema.required.forEach(::add) })
            addProperty("additionalProperties", schema.additionalProperties)
        }
        schema.items?.let { add("items", encodeNode(it)) }
        schema.enumValues?.let {
            add(
                "enum",
                JsonArray().apply { it.forEach { value -> add(value.deepCopy()) } })
        }
        schema.minimum?.let { addProperty("minimum", it) }
        schema.maximum?.let { addProperty("maximum", it) }
        schema.minLength?.let { addProperty("minLength", it) }
        schema.maxLength?.let { addProperty("maxLength", it) }
        schema.minItems?.let { addProperty("minItems", it) }
        schema.maxItems?.let { addProperty("maxItems", it) }
    }
}
