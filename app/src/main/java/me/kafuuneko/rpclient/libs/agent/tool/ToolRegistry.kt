package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson

/** 单个定义的冻结投影；指纹同时绑定版本、执行性质及参数约束。 */
class RegisteredAgentTool internal constructor(
    val definition: AgentToolDefinition,
    val snapshotJson: String,
    val digest: String,
    internal val handler: AgentTool,
)

/**
 * 通过依赖注入集合建立稳定工具目录。
 * - 启动时拒绝 ID、模型函数名冲突及非法 Schema。
 * - 对定义中的可变集合深拷贝，防止注册后修改参数契约。
 */
class ToolRegistry(tools: List<AgentTool>) {
    private val mTools = tools.map { tool ->
        ToolSchemaValidator.validateDefinition(tool.definition.parameters)
        val definition = tool.definition.copy(parameters = freeze(tool.definition.parameters))
        require(definition.toolId.matches(Regex("^[a-zA-Z0-9_.-]{1,128}$"))) { "Invalid tool id" }
        require(definition.name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]{0,63}$"))) { "Invalid tool function name" }
        require(definition.contractVersion > 0 && definition.parameters.type == ToolSchema.Type.Object) { "Invalid tool contract" }
        require(!definition.parallelSafe || definition.effect == ToolEffect.ReadOnly) { "Only deterministic reads can run in parallel" }
        require(!definition.writesMemory || definition.effect == ToolEffect.LocalWrite) { "Memory writes must be local" }
        val snapshot = JsonObject().apply {
            addProperty("toolId", definition.toolId)
            addProperty("name", definition.name)
            addProperty("version", definition.contractVersion)
            addProperty("description", definition.description)
            addProperty("effect", definition.effect.name)
            addProperty("parallelSafe", definition.parallelSafe)
            addProperty("writesMemory", definition.writesMemory)
            add("parameters", ToolSchemaCodec.encode(definition.parameters))
        }
        val json = AgentJson.canonical(snapshot)
        require(json.toByteArray(Charsets.UTF_8).size <= ToolSchemaValidator.MAX_ARGUMENT_BYTES) { "Tool definition is too large" }
        RegisteredAgentTool(definition, json, AgentJson.digest(json), tool)
    }

    init {
        require(mTools.map { it.definition.toolId }
            .distinct().size == mTools.size) { "Duplicate tool id" }
        require(mTools.map { it.definition.name }
            .distinct().size == mTools.size) { "Duplicate tool function name" }
    }

    /** 根据客户端稳定 ID 查找；未知工具不回退到模型提供的名称。 */
    fun find(toolId: String): RegisteredAgentTool? =
        mTools.firstOrNull { it.definition.toolId == toolId }

    /** 保持注册顺序并按会话白名单选择，用于冻结模型请求前缀。 */
    fun available(enabledIds: Set<String>): List<RegisteredAgentTool> =
        mTools.filter { it.definition.toolId in enabledIds }

    private fun freeze(schema: ToolSchema): ToolSchema = schema.copy(
        properties = schema.properties.mapValues { freeze(it.value) },
        required = schema.required.toSet(),
        items = schema.items?.let(::freeze),
        enumValues = schema.enumValues?.map { it.deepCopy() },
    )
}
