package me.kafuuneko.rpclient.libs.agent.connection

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.UUID
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.room.entity.ToolConnection
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition

/**
 * 配置编辑器与便携包共享的显式字段编解码器。
 * - 验证稳定身份、函数名、Schema 和结构化映射。
 * - 不对 Room 实体使用反射序列化，导出内容只包含凭据引用。
 */
object ToolConfigCodec {
    const val MAX_PACKAGE_BYTES = 2 * 1024 * 1024
    val LOCAL_NAMES = setOf(
        "search_lore",
        "read_lore_entry",
        "search_chat",
        "read_messages",
        "search_memory",
        "read_memory",
        "upsert_memory",
        "correct_memory",
        "invalidate_memory",
        "promote_memory_scope",
        "roll_dice",
        "calculate",
        "get_scene_state",
        "update_scene_state",
        "ask_user"
    )
    private val mPretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /**
     * 编辑连接所需的非敏感 JSON。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @return 可编辑、可导出的非敏感 JSON 对象。
     */
    fun connection(value: ToolConnection): JsonObject = JsonObject().apply {
        addProperty("id", value.id)
        addProperty("name", value.name)
        addProperty("enabled", value.enabled)
        add("config", AgentJson.parse(value.configJson, maxDepth = 24))
    }

    /**
     * 编辑工具所需的参数与结构化映射。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @return 保留契约与映射的 JSON 对象。
     */
    fun tool(value: ToolDefinition): JsonObject = JsonObject().apply {
        addProperty("id", value.id)
        addProperty("name", value.name)
        addProperty("description", value.description)
        addProperty("enabled", value.enabled)
        addProperty("kind", value.kind)
        value.connectionId?.let { addProperty("connectionId", it) }
        add("parameters", AgentJson.parse(value.parametersJson, maxDepth = 32))
        add("mapping", AgentJson.parse(value.mappingJson, maxDepth = 24))
    }

    /**
     * 读取编辑内容，稳定身份由调用者提供而非从文件信任。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @param id 客户端持有的稳定身份。
     * @return 通过校验的连接配置。
     */
    fun readConnection(value: JsonObject, id: String): ToolConnection {
        requireConfig(UUID.fromString(id).toString() == id, "invalid_json", "Invalid connection identity")
        requireConfig(
            value.keySet().all { it in setOf("id", "name", "enabled", "config") },
            "invalid_json",
            "Unknown connection field",
        )
        val configObject = value["config"]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw ToolConfigValidationException("invalid_json", "Missing connection config")
        val config = HttpToolConfigCodec.connection(configObject.toString())
        val name = value["name"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString?.trim().orEmpty()
        requireConfig(name.isNotEmpty() && name.length <= 120, "invalid_name", "Invalid connection name")
        return ToolConnection(id, name, HttpToolConfigCodec.encode(config), enabled(value))
    }

    /**
     * 严格校验跨协议名字及 Schema；分页参数由运行时补充，禁止配置抢占。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @param id 客户端持有的稳定身份。
     * @return 通过校验的工具定义。
     */
    fun readTool(value: JsonObject, id: String): ToolDefinition {
        // 校验工具顶层字段与跨五协议函数名，禁止占用内置工具名称。
        requireConfig(UUID.fromString(id).toString() == id, "invalid_json", "Invalid tool identity")
        requireConfig(
            value.keySet().all {
                it in setOf(
                    "id",
                    "name",
                    "description",
                    "enabled",
                    "kind",
                    "connectionId",
                    "parameters",
                    "mapping"
                )
            },
            "invalid_json",
            "Unknown tool field",
        )
        val name = value["name"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString.orEmpty()
        requireConfig(
            name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]{0,63}$")) && name !in LOCAL_NAMES,
            "invalid_name",
            "Invalid tool name",
        )
        // 解码并校验参数 Schema，保留 page_offset 给客户端分页机制。
        val parametersObject = value["parameters"]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw ToolConfigValidationException("invalid_schema", "Missing parameter schema")
        val schema = try {
            ToolSchemaCodec.decode(parametersObject.toString())
        } catch (error: Exception) {
            throw ToolConfigValidationException("invalid_schema", error.message ?: "Invalid parameter schema")
        }
        requireConfig(
            schema.type == ToolSchema.Type.Object && "page_offset" !in schema.properties,
            "invalid_schema",
            "Invalid HTTP parameter schema",
        )
        val kind = value["kind"]?.asString ?: "Http"
        requireConfig(kind in setOf("Http", "ReadUrl"), "invalid_json", "Invalid HTTP tool kind")
        val description = value["description"]?.asString.orEmpty()
        requireConfig(description.length <= 8000, "invalid_description", "Tool description is too long")
        // 映射先于写库解析，未知脚本字段或主机绑定均不能落库。
        val mappingRaw = value["mapping"]?.takeIf { it.isJsonObject }?.asJsonObject?.toString() ?: "{}"
        val mapping = HttpToolConfigCodec.mapping(mappingRaw)
        val connection = value["connectionId"]?.takeUnless { it.isJsonNull }?.asString
        if (kind == "ReadUrl") {
            requireConfig(
                connection == null &&
                    schema.properties["url"]?.type == ToolSchema.Type.String &&
                    "url" in schema.required,
                "invalid_schema",
                "Invalid read URL template",
            )
        }
        return ToolDefinition(
            id = id,
            name = name,
            description = description,
            parametersJson = ToolSchemaCodec.encode(schema).toString(),
            mappingJson = HttpToolConfigCodec.encode(mapping),
            connectionId = connection,
            kind = kind,
            enabled = enabled(value),
        ).also { tool ->
            // 保存时按最长执行性质计量完整运行快照，后续连接改为写方法也不能撑破上限。
            try {
                ToolRegistry.snapshot(definition(tool, null).copy(effect = ToolEffect.ExternalWrite))
            } catch (error: Exception) {
                throw ToolConfigValidationException("invalid_schema", error.message ?: "Tool definition is too large")
            }
        }
    }

    /**
     * 校验工具映射中的参数引用与绑定连接的 HTTP 方法是否兼容，供保存、导入与恢复共用。
     *
     * @param tool 已完成自身结构校验的工具定义。
     * @param connectionMethod 绑定连接的 HTTP 方法，未绑定时为空。
     */
    fun validateBindings(tool: ToolDefinition, connectionMethod: String?) {
        // 先校验映射与 Schema 本身结构合法。
        val mapping = HttpToolConfigCodec.mapping(tool.mappingJson)
        val schema = try {
            ToolSchemaCodec.decode(tool.parametersJson)
        } catch (error: Exception) {
            throw ToolConfigValidationException("invalid_schema", error.message ?: "Invalid parameter schema")
        }
        // 确保每个绑定引用的根参数均存在于 Schema properties 中。
        requireConfig(
            mapping.bindings.all { binding ->
                val first = ToolJsonPointer.segments(binding.argument).firstOrNull()
                first != null && first in schema.properties
            },
            "invalid_mapping",
            "HTTP binding references unknown argument",
        )
        // GET/HEAD 请求不允许携带 body 绑定，避免运行期必然失败。
        if (connectionMethod != null) {
            requireConfig(
                connectionMethod !in setOf("GET", "HEAD") ||
                    mapping.bindings.none { it.target == "body" },
                "invalid_mapping",
                "Read requests cannot bind a body",
            )
        }
    }

    /**
     * 从权威配置构建运行契约，保存、恢复和实际派发共享客户端分页参数。
     *
     * @param tool 已完成配置校验的工具。
     * @param connection 本次冻结的连接；待配置工具可为空。
     * @return 绑定配置指纹的跨协议工具定义。
     */
    fun definition(tool: ToolDefinition, connection: ToolConnection?): AgentToolDefinition {
        val config = connection?.let { HttpToolConfigCodec.connection(it.configJson) }
        val schema = ToolSchemaCodec.decode(tool.parametersJson)
        // 本地页偏移由程序补入契约，连接和映射只通过不可逆指纹进入模型目录。
        val pagedSchema = schema.copy(
            properties = schema.properties + ("page_offset" to ToolSchema(
                type = ToolSchema.Type.Integer, minimum = 0.0, maximum = 2_097_152.0,
                description = "Local page offset from nextPageOffset. Omit for the first page.",
            ))
        )
        return AgentToolDefinition(
            toolId = "http.${tool.id}", name = tool.name, contractVersion = 1,
            description = tool.description, parameters = pagedSchema,
            effect = if (config?.method in setOf(
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE"
                )
            ) ToolEffect.ExternalWrite else ToolEffect.ReadOnly,
            external = true,
            configurationDigest = AgentJson.digest(
                ToolConfigCodec.tool(tool).toString() + (connection?.configJson ?: "")
            ),
        )
    }

    /**
     * 输出供用户编辑的缩进 JSON；不执行宏展开。
     *
     * @param value 经过领域校验的配置或 JSON 值。
     * @return 供配置编辑器展示的格式化文本。
     */
    fun pretty(value: JsonObject): String = mPretty.toJson(value)

    /**
     * 解析编辑器的有界 JSON，Schema 树允许结构层次多于普通参数。
     *
     * @param raw 待严格解析的 JSON 或地址文本。
     * @return 通过大小、深度和重复键检查的对象。
     */
    fun parse(raw: String): JsonObject = try {
        val parsed = AgentJson.parse(raw, maxBytes = 256 * 1024, maxDepth = 40)
        requireConfig(parsed.isJsonObject, "invalid_json", "Expected JSON object")
        parsed.asJsonObject
    } catch (error: ToolConfigValidationException) {
        throw error
    } catch (error: Exception) {
        throw ToolConfigValidationException("invalid_json", error.message ?: "Invalid JSON")
    }

    /**
     * 创建独立的凭据引用便携包，不包含本机密文或检查点。
     *
     * @param connections 不含凭据正文的连接快照。
     * @param tools 待导出的工具定义快照。
     * @param overrides 按内置工具 ID 保存的稀疏覆盖。
     * @return 不含凭据正文与本机检查点的便携包。
     */
    fun export(
        connections: List<ToolConnection>,
        tools: List<ToolDefinition>,
        overrides: Map<String, String>
    ): String {
        val value = JsonObject().apply {
            addProperty("format", "rpclient.http-tools")
            addProperty("version", 1)
            add("connections", JsonArray().apply { connections.forEach { add(connection(it)) } })
            add("tools", JsonArray().apply { tools.forEach { add(tool(it)) } })
            add(
                "overrides",
                JsonObject().apply { overrides.forEach { (id, json) -> add(id, parse(json)) } })
        }
        val json = mPretty.toJson(value)
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_PACKAGE_BYTES) { "Tool package is too large" }
        return json
    }

    private fun enabled(value: JsonObject): Boolean {
        val field = value["enabled"] ?: return true
        require(field.asJsonPrimitive.isBoolean) { "Invalid tool enabled setting" }
        return field.asBoolean
    }
}
