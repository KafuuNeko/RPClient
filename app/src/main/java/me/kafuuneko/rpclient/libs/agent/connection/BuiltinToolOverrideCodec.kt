package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaValidator

/** 内置工具稀疏覆写的结构化读写模型，供表单编辑器与序列化逻辑复用。 */
data class BuiltinToolOverrideData(
    val displayName: String = "",
    val description: String = "",
    val usageHint: String = "",
    val parameterDescriptions: Map<String, String> = emptyMap(),
    val defaults: Map<String, Int> = emptyMap(),
)

/**
 * 内置工具稀疏覆盖，只改变说明和契约允许的可选默认值。
 * - 参数类型、必填性、执行性质及模型函数名不能被编辑器覆盖。
 * - 缺省参数与显式 null 分别处理，默认值不覆盖模型的明确输入。
 * - 具名装饰器完整委托 [AgentTool.isEnabled]，避免覆盖层丢失原工具启停判断。
 */
object BuiltinToolOverrideCodec {
    private val ALLOWED_KEYS = setOf(
        "displayName",
        "description",
        "usageHint",
        "parameterDescriptions",
        "defaults",
    )
    private val EDITABLE_DEFAULT_KEYS = listOf("limit", "offset")

    /**
     * 校验覆盖后返回冻结包装处理器，空覆盖保持原处理器。
     *
     * @param tool 冻结的工具契约及处理器。
     * @param raw 待严格解析的 JSON 文本。
     * @return 通过校验的包装处理器。
     */
    fun apply(tool: AgentTool, raw: String?): AgentTool {
        if (raw == null) return tool
        // 先校验基础形状和当前内置工具的参数契约，防止非法参数或必填默认值进入运行期。
        val value = ToolConfigCodec.parse(raw)
        validateForSchema(tool.definition.toolId, tool.definition.parameters, value)
        if (isEmpty(value)) return tool
        val defaults = value.getAsJsonObject("defaults") ?: JsonObject()
        val parameterDescriptions = value.getAsJsonObject("parameterDescriptions") ?: JsonObject()
        val schema = tool.definition.parameters
        // 空白说明回退到内置默认说明，避免保存空串意外剥离模型工具文档。
        val baseDescription = value["description"]?.asString?.takeIf { it.isNotBlank() }
            ?: tool.definition.description
        val usageHint = value["usageHint"]?.asString?.takeIf { it.isNotBlank() }.orEmpty()
        val definition = tool.definition.copy(
            description = listOf(baseDescription, usageHint)
                .filter { it.isNotBlank() }
                .joinToString("\n"),
            parameters = schema.copy(properties = schema.properties.mapValues { (name, child) ->
                val customDescription = parameterDescriptions[name]?.asString?.takeIf { it.isNotBlank() }
                child.copy(description = customDescription ?: child.description)
            }),
            configurationDigest = AgentJson.digest(raw),
        )
        return OverriddenAgentTool(
            mDelegate = tool,
            definition = definition,
            mDefaults = defaults.deepCopy(),
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
            ?.associate { (key, item) -> key to item.asBigDecimal.intValueExact() }
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
                data.defaults.forEach { (key, number) -> addProperty(key, number) }
            })
        }
    }.toString()

    /** 返回指定内置工具允许配置默认值的可选分页参数列表。 */
    fun editableDefaultKeys(schema: ToolSchema): List<String> =
        EDITABLE_DEFAULT_KEYS.filter { key -> key in schema.properties && key !in schema.required }

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
    fun validateForSchema(id: String, schema: ToolSchema, value: JsonObject) {
        validateShape(id, value)
        // 必填参数不得因用户默认值而隐式变成可选，保护授权和证据契约。
        val defaults = value.getAsJsonObject("defaults") ?: JsonObject()
        defaults.entrySet().forEach { (name, child) ->
            val property = schema.properties[name]
                ?: throw ToolConfigValidationException("invalid_override", "Unknown default parameter")
            requireConfig(
                name in EDITABLE_DEFAULT_KEYS &&
                    name !in schema.required &&
                    ToolSchemaValidator.validate(property, child).isEmpty(),
                "invalid_override",
                "Invalid built-in default",
            )
        }
        val parameterDescriptions = value.getAsJsonObject("parameterDescriptions") ?: JsonObject()
        requireConfig(
            parameterDescriptions.keySet().all { it in schema.properties },
            "invalid_override",
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
            id in ToolConfigCodec.LOCAL_NAMES && value.keySet().all { it in ALLOWED_KEYS },
            "invalid_override",
            "Invalid built-in override",
        )
        // 校验文本说明字段类型与长度上限。
        listOf("displayName", "description", "usageHint").forEach { key ->
            if (value.has(key)) {
                val element = value[key]
                requireConfig(
                    element.isJsonPrimitive && element.asJsonPrimitive.isString &&
                        element.asString.length <= 8000,
                    "invalid_override",
                    "Invalid built-in description",
                )
            }
        }
        // 校验参数说明映射与分页默认值类型和区间。
        if (value.has("parameterDescriptions")) {
            val map = value["parameterDescriptions"]
            requireConfig(map.isJsonObject, "invalid_override", "Invalid parameter descriptions")
            map.asJsonObject.entrySet().forEach { (_, text) ->
                requireConfig(
                    text.isJsonPrimitive && text.asJsonPrimitive.isString && text.asString.length <= 2000,
                    "invalid_override",
                    "Invalid parameter description",
                )
            }
        }
        if (value.has("defaults")) {
            val defaults = value["defaults"]
            requireConfig(defaults.isJsonObject, "invalid_override", "Invalid built-in defaults")
            defaults.asJsonObject.entrySet().forEach { (name, child) ->
                requireConfig(
                    name in EDITABLE_DEFAULT_KEYS && child.isJsonPrimitive && child.asJsonPrimitive.isNumber,
                    "invalid_override",
                    "Invalid built-in default",
                )
                val count = try {
                    child.asBigDecimal.intValueExact()
                } catch (error: ArithmeticException) {
                    throw ToolConfigValidationException("invalid_override", "Invalid built-in default")
                }
                requireConfig(
                    if (name == "limit") count in 1..20 else count >= 0,
                    "invalid_override",
                    "Invalid built-in page default",
                )
            }
        }
    }

    /** 完整转发启停与准备调用的具名内置覆盖装饰器。 */
    private class OverriddenAgentTool(
        private val mDelegate: AgentTool,
        override val definition: AgentToolDefinition,
        private val mDefaults: JsonObject,
    ) : AgentTool {
        override suspend fun isEnabled(): Boolean = mDelegate.isEnabled()

        override suspend fun prepare(
            context: ToolExecutionContext,
            arguments: JsonObject,
        ): ToolAction {
            val effective = mDefaults.deepCopy()
            arguments.entrySet().forEach { (name, child) -> effective.add(name, child.deepCopy()) }
            return mDelegate.prepare(context, effective)
        }
    }
}
