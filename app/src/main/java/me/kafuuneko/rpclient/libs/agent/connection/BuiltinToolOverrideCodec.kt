package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaValidator
import me.kafuuneko.rpclient.libs.agent.tool.catalog.BuiltinToolRegistrations
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EditableDefaultSpec
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolSpecification

/** 内置工具稀疏覆写的结构化读写模型，供表单编辑器与序列化逻辑复用。 */
data class BuiltinToolOverrideData(
    val displayName: String = "",
    val description: String = "",
    val usageHint: String = "",
    val parameterDescriptions: Map<String, String> = emptyMap(),
    val defaults: Map<String, JsonElement> = emptyMap(),
)

/**
 * 内置工具稀疏覆盖，只改变说明和契约允许的可选默认值。
 * - 参数类型、必填性、执行性质及模型函数名不能被编辑器覆盖。
 * - 缺省参数与显式 null 分别处理，默认值不覆盖模型的明确输入。
 * - 仅解析配置，不创建处理器或覆盖实时可用性策略。
 */
object BuiltinToolOverrideCodec {
    private val ALLOWED_KEYS = setOf(
        "displayName",
        "description",
        "usageHint",
        "parameterDescriptions",
        "defaults",
    )

    /**
     * 根据规格解析冻结配置，展示名仅由界面使用。
     * @param specification 原始契约和可编辑默认值规格。
     * @param raw 稀疏覆写；空值沿用内置配置。
     * @return 直接交给工具构造函数的执行配置。
     */
    fun resolve(specification: ToolSpecification, raw: String?): EffectiveToolConfig {
        val original = specification.definition()
        if (raw == null) return EffectiveToolConfig(original)
        val value = ToolConfigCodec.parse(raw)
        validateForSchema(
            specification.toolId,
            original.parameters,
            value,
            specification.editableDefaults
        )
        // 展示元数据不参与兼容性判断，空白覆盖仍回退原始模型说明。
        val execution = value.deepCopy().apply { remove("displayName") }
        if (isEmpty(execution)) return EffectiveToolConfig(original)
        val data = decode(raw)
        val description =
            listOf(data.description.takeIf { it.isNotBlank() } ?: original.description,
                data.usageHint).filter { it.isNotBlank() }.joinToString("\n")
        val definition = original.copy(
            description = description,
            parameters = original.parameters.copy(properties = original.parameters.properties.mapValues { (key, child) ->
                child.copy(description = data.parameterDescriptions[key]?.takeIf { it.isNotBlank() }
                    ?: child.description)
            }),
            configurationDigest = AgentJson.digest(AgentJson.canonical(execution)),
        )
        return EffectiveToolConfig(
            definition,
            execution.getAsJsonObject("defaults")?.deepCopy() ?: JsonObject()
        )
    }

    /**
     * 解码内置覆写为结构化表单模型，缺失时返回空覆盖。
     *
     * @param raw 持久化的覆写 JSON，为空时返回空覆盖。
     * @return 供编辑器展示与修改的稀疏覆写数据。
     */
    fun decode(raw: String?): BuiltinToolOverrideData {
        // 空内容直接返回空覆盖，表示沿用全部内置默认配置。
        if (raw.isNullOrBlank()) return BuiltinToolOverrideData()
        val value = ToolConfigCodec.parse(raw)
        val paramDescriptions = value.getAsJsonObject("parameterDescriptions")?.entrySet()
            ?.associate { (key, item) -> key to item.asString }
            .orEmpty()
        val defaults = value.getAsJsonObject("defaults")?.entrySet()
            ?.associate { (key, item) -> key to item.deepCopy() }
            .orEmpty()
        return BuiltinToolOverrideData(
            displayName = value["displayName"]?.asString.orEmpty(),
            description = value["description"]?.asString.orEmpty(),
            usageHint = value["usageHint"]?.asString.orEmpty(),
            parameterDescriptions = paramDescriptions,
            defaults = defaults,
        )
    }

    /**
     * 将结构化表单模型编码为紧凑稀疏 JSON，忽略空白未覆写字段。
     *
     * @param data 编辑器提交的稀疏覆写数据。
     * @return 规范化后的覆写 JSON 字符串。
     */
    fun encode(data: BuiltinToolOverrideData): String = JsonObject().apply {
        // 仅序列化非空覆盖项，保持稀疏结构便于恢复默认。
        data.displayName.trim().takeIf { it.isNotEmpty() }?.let { addProperty("displayName", it) }
        data.description.trim().takeIf { it.isNotEmpty() }?.let { addProperty("description", it) }
        data.usageHint.trim().takeIf { it.isNotEmpty() }?.let { addProperty("usageHint", it) }
        val cleanedParams = data.parameterDescriptions
            .mapValues { (_, text) -> text.trim() }
            .filterValues { it.isNotEmpty() }
        if (cleanedParams.isNotEmpty()) {
            add("parameterDescriptions", JsonObject().apply {
                cleanedParams.forEach { (key, text) -> addProperty(key, text) }
            })
        }
        if (data.defaults.isNotEmpty()) {
            add("defaults", JsonObject().apply {
                data.defaults.forEach { (key, value) -> add(key, value.deepCopy()) }
            })
        }
    }.toString()

    /** 判断覆写对象是否未包含任何有效自定义项，避免保存空白记录误标为已自定义。 */
    fun isEmpty(value: JsonObject): Boolean {
        val hasText = listOf("displayName", "description", "usageHint").any { key ->
            value[key]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.isNotBlank() == true
        }
        val hasParamDesc = value.getAsJsonObject("parameterDescriptions")?.entrySet()
            ?.any { (_, text) -> text.isJsonPrimitive && text.asJsonPrimitive.isString && text.asString.isNotBlank() } == true
        val hasDefaults = value.getAsJsonObject("defaults")?.size()?.let { it > 0 } == true
        return !hasText && !hasParamDesc && !hasDefaults
    }

    /**
     * 结合内置工具真实 Schema 执行完整校验，供保存、导入与整库恢复共享。
     *
     * @param id 内置工具稳定身份。
     * @param schema 目标内置工具的权威参数 Schema。
     * @param value 已解析的覆写 JSON 对象。
     */
    fun validateForSchema(
        id: String, schema: ToolSchema, value: JsonObject,
        editableDefaults: List<EditableDefaultSpec> = BuiltinToolRegistrations.catalog.find(id)?.specification?.editableDefaults.orEmpty(),
    ) {
        validateShape(id, value)
        // 必填参数不得因用户默认值而隐式变成可选，保护授权和证据契约。
        val defaults = value.getAsJsonObject("defaults") ?: JsonObject()
        defaults.entrySet().forEach { (name, child) ->
            val property = schema.properties[name]
                ?: throw ToolConfigValidationException(
                    ToolErrorCode.INVALID_OVERRIDE,
                    "Unknown default parameter"
                )
            requireConfig(
                editableDefaults.any { it.key == name } &&
                    name !in schema.required &&
                    ToolSchemaValidator.validate(property, child).isEmpty(),
                ToolErrorCode.INVALID_OVERRIDE,
                "Invalid built-in default",
            )
        }
        val parameterDescriptions = value.getAsJsonObject("parameterDescriptions") ?: JsonObject()
        requireConfig(
            parameterDescriptions.keySet().all { it in schema.properties },
            ToolErrorCode.INVALID_OVERRIDE,
            "Unknown parameter description",
        )
    }

    /**
     * 校验覆写 JSON 的顶层结构、字段类型和分页范围边界。
     *
     * @param id 客户端持有的稳定身份。
     * @param value 经过解析的覆写 JSON 对象。
     */
    fun validateShape(id: String, value: JsonObject) {
        requireConfig(
            id.isNotBlank() && value.keySet().all { it in ALLOWED_KEYS },
            ToolErrorCode.INVALID_OVERRIDE,
            "Invalid built-in override",
        )
        // 校验文本说明字段类型与长度上限。
        listOf("displayName", "description", "usageHint").forEach { key ->
            if (value.has(key)) {
                val element = value[key]
                requireConfig(
                    element.isJsonPrimitive && element.asJsonPrimitive.isString &&
                        element.asString.length <= 8000,
                    ToolErrorCode.INVALID_OVERRIDE,
                    "Invalid built-in description",
                )
            }
        }
        // 校验参数说明映射与分页默认值类型和区间。
        if (value.has("parameterDescriptions")) {
            val map = value["parameterDescriptions"]
            requireConfig(
                map.isJsonObject,
                ToolErrorCode.INVALID_OVERRIDE,
                "Invalid parameter descriptions"
            )
            map.asJsonObject.entrySet().forEach { (_, text) ->
                requireConfig(
                    text.isJsonPrimitive && text.asJsonPrimitive.isString && text.asString.length <= 2000,
                    ToolErrorCode.INVALID_OVERRIDE,
                    "Invalid parameter description",
                )
            }
        }
        if (value.has("defaults")) {
            val defaults = value["defaults"]
            requireConfig(
                defaults.isJsonObject,
                ToolErrorCode.INVALID_OVERRIDE,
                "Invalid built-in defaults"
            )

        }
    }

}
