package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/** 显式编码 Schema 快照，不依赖 Gson 反射字段名，R8 后仍保持稳定。 */
object ToolSchemaCodec {
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
