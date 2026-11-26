package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * 跨协议公共参数 Schema，缺省属性与显式 null 分别校验。
 * - 协议编码器读取同一棵树，不能通过补 null 改写必填语义。
 * - 对象默认拒绝未知字段；细粒度约束即使无法发送给协议也必须在本地执行。
 */
data class ToolSchema(
    val type: Type,
    val description: String = "",
    val nullable: Boolean = false,
    val properties: Map<String, ToolSchema> = emptyMap(),
    val required: Set<String> = emptySet(),
    val additionalProperties: Boolean = false,
    val items: ToolSchema? = null,
    val enumValues: List<JsonElement>? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minItems: Int? = null,
    val maxItems: Int? = null,
) {
    enum class Type { Object, Array, String, Integer, Number, Boolean, Null }
}

/** 只暴露路径和稳定错误码，不将模型参数正文拼入诊断或页面状态。 */
data class ToolSchemaViolation(val path: String, val code: String)

/**
 * Schema 定义与参数的统一校验器。
 * - 限制深度、节点数和参数字节数，避免模型或导入定义耗尽资源。
 * - 数值不经 Double 取整，避免大整数与小数的误判。
 */
object ToolSchemaValidator {
    const val MAX_DEPTH = 8
    const val MAX_ARGUMENT_BYTES = 64 * 1024
    private const val MAX_SCHEMA_NODES = 1024

    /** 校验注册定义自身的一致性，非法定义在暴露给模型之前失败。 */
    fun validateDefinition(schema: ToolSchema) {
        var nodes = 0
        fun visit(node: ToolSchema, depth: Int) {
            require(depth <= MAX_DEPTH && ++nodes <= MAX_SCHEMA_NODES) { "Tool schema is too complex" }
            require(node.required.all(node.properties::containsKey)) { "Unknown required property" }
            require(
                node.type == ToolSchema.Type.Object ||
                    (node.properties.isEmpty() && node.required.isEmpty() && !node.additionalProperties)
            ) {
                "Object constraints on non-object schema"
            }
            require((node.type == ToolSchema.Type.Array) == (node.items != null)) { "Invalid array schema" }
            require(node.minimum == null || node.minimum.isFinite()) { "Invalid numeric minimum" }
            require(node.maximum == null || node.maximum.isFinite()) { "Invalid numeric maximum" }
            require(node.minimum == null || node.maximum == null || node.minimum <= node.maximum) { "Invalid numeric range" }
            validateRange(node.minLength, node.maxLength)
            validateRange(node.minItems, node.maxItems)
            require(
                node.type in setOf(ToolSchema.Type.Integer, ToolSchema.Type.Number) ||
                    (node.minimum == null && node.maximum == null)
            ) { "Numeric constraints on non-number schema" }
            require(
                node.type == ToolSchema.Type.String ||
                    (node.minLength == null && node.maxLength == null)
            ) { "String constraints on non-string schema" }
            require(
                node.type == ToolSchema.Type.Array ||
                    (node.minItems == null && node.maxItems == null)
            ) { "Array constraints on non-array schema" }
            // 枚举项也需满足类型和范围，不能以枚举绕过普通参数规则。
            node.enumValues?.let { values ->
                require(values.isNotEmpty() && values.distinct().size == values.size) { "Invalid enum values" }
                require(values.all {
                    validate(
                        node.copy(enumValues = null),
                        it
                    ).isEmpty()
                }) { "Invalid enum value" }
            }
            node.properties.values.forEach { visit(it, depth + 1) }
            node.items?.let { visit(it, depth + 1) }
        }
        visit(schema, 1)
    }

    /** 返回有限错误列表；调用方只在无错误时派发工具。 */
    fun validate(schema: ToolSchema, value: JsonElement): List<ToolSchemaViolation> {
        if (value.toString().toByteArray(Charsets.UTF_8).size > MAX_ARGUMENT_BYTES) {
            return listOf(ToolSchemaViolation("$", "arguments_too_large"))
        }
        val errors = mutableListOf<ToolSchemaViolation>()
        validateValue(schema, value, "$", 1, errors)
        return errors.take(20)
    }

    /** 参数树递归前检查深度，错误路径只保留 Schema 内已知的属性名。 */
    private fun validateValue(
        schema: ToolSchema,
        value: JsonElement,
        path: String,
        depth: Int,
        errors: MutableList<ToolSchemaViolation>,
    ) {
        if (errors.size >= 20) return
        if (depth > MAX_DEPTH) {
            errors += ToolSchemaViolation(path, "max_depth")
            return
        }
        if (value.isJsonNull && (schema.nullable || schema.type == ToolSchema.Type.Null)) {
            if (schema.enumValues != null && value !in schema.enumValues) errors += ToolSchemaViolation(
                path,
                "enum"
            )
            return
        }
        if (!matchesType(schema.type, value)) {
            errors += ToolSchemaViolation(path, "type")
            return
        }
        if (schema.enumValues != null && value !in schema.enumValues) errors += ToolSchemaViolation(
            path,
            "enum"
        )
        // 按类型执行局部约束，不对字符串数字或布尔值作隐式类型转换。
        when (schema.type) {
            ToolSchema.Type.Object -> validateObject(
                schema,
                value.asJsonObject,
                path,
                depth,
                errors
            )

            ToolSchema.Type.Array -> {
                val array = value.asJsonArray
                if (!inRange(
                        array.size(),
                        schema.minItems,
                        schema.maxItems
                    )
                ) errors += ToolSchemaViolation(path, "array_length")
                array.forEachIndexed { index, item ->
                    validateValue(
                        requireNotNull(schema.items),
                        item,
                        "$path[$index]",
                        depth + 1,
                        errors
                    )
                }
            }

            ToolSchema.Type.String -> {
                val string = value.asString
                if (!inRange(
                        string.codePointCount(0, string.length),
                        schema.minLength,
                        schema.maxLength
                    )
                ) errors += ToolSchemaViolation(path, "string_length")
            }

            ToolSchema.Type.Number, ToolSchema.Type.Integer -> {
                val number = value.asBigDecimal
                if ((schema.minimum != null && number < schema.minimum.toBigDecimal()) ||
                    (schema.maximum != null && number > schema.maximum.toBigDecimal())
                ) errors += ToolSchemaViolation(path, "number_range")
            }

            else -> Unit
        }
    }

    private fun validateObject(
        schema: ToolSchema,
        value: JsonObject,
        path: String,
        depth: Int,
        errors: MutableList<ToolSchemaViolation>
    ) {
        // has 区分缺失属性和显式 null；未知属性不回显原文。
        schema.required.filterNot(value::has)
            .forEach { errors += ToolSchemaViolation("$path.$it", "required") }
        value.entrySet().forEach { (name, child) ->
            val property = schema.properties[name]
            if (property != null) validateValue(property, child, "$path.$name", depth + 1, errors)
            else if (!schema.additionalProperties) errors += ToolSchemaViolation(
                path,
                "additional_property"
            )
        }
    }

    private fun matchesType(type: ToolSchema.Type, value: JsonElement): Boolean = when (type) {
        ToolSchema.Type.Object -> value.isJsonObject
        ToolSchema.Type.Array -> value.isJsonArray
        ToolSchema.Type.String -> value.isJsonPrimitive && value.asJsonPrimitive.isString
        ToolSchema.Type.Boolean -> value.isJsonPrimitive && value.asJsonPrimitive.isBoolean
        ToolSchema.Type.Null -> value.isJsonNull
        ToolSchema.Type.Number, ToolSchema.Type.Integer -> value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
            runCatching {
                val number = value.asBigDecimal
                type == ToolSchema.Type.Number || number.stripTrailingZeros().scale() <= 0
            }.getOrDefault(false)
    }

    private fun validateRange(minimum: Int?, maximum: Int?) {
        require(
            (minimum == null || minimum >= 0) && (maximum == null || maximum >= 0) &&
                (minimum == null || maximum == null || minimum <= maximum)
        ) { "Invalid length range" }
    }

    private fun inRange(value: Int, minimum: Int?, maximum: Int?): Boolean =
        (minimum == null || value >= minimum) && (maximum == null || value <= maximum)
}
