package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class ToolRegistry(
    private val mBase: List<AgentTool>,
    private val mSource: (suspend () -> List<AgentTool>)? = null,
) {
    private val mMutex = Mutex()
    private val mVersions = ConcurrentHashMap<String, RegisteredAgentTool>()

    @Volatile
    private var mTools = register(mBase)

    /** 新运行读取配置快照；旧运行继续按已冻结指纹定位原处理器。 */
    suspend fun refresh() {
        val source = mSource ?: return
        mMutex.withLock {
            val dynamic = source()
            val replaced = dynamic.mapTo(HashSet()) { it.definition.toolId }
            val next = register(mBase.filterNot { it.definition.toolId in replaced } + dynamic)
            // 仅在版本变化时写入历史映射，并限制非活动缓存上限防止长期运行内存无限增长。
            mTools.forEach { mVersions[it.digest] = it }
            next.forEach { mVersions[it.digest] = it }
            mTools = next
            if (mVersions.size > MAX_CACHED_VERSIONS) {
                val activeDigests = next.mapTo(HashSet()) { it.digest }
                val removable = mVersions.keys.filterNot { it in activeDigests }
                removable.take(mVersions.size - MAX_CACHED_VERSIONS).forEach { mVersions.remove(it) }
            }
        }
    }

    /** 整库替换时清理旧库处理器，避免相同 ID 与旧配置碰撞。 */
    suspend fun clear() = mMutex.withLock {
        mVersions.clear()
        mTools = register(mBase)
    }

    private fun register(tools: List<AgentTool>): List<RegisteredAgentTool> {
        // 先验证完整目录再发布快照，非法配置不能留下半套已注册工具。
        val registered = tools.map { tool ->
            ToolSchemaValidator.validateDefinition(tool.definition.parameters)
            val definition = tool.definition.copy(parameters = freeze(tool.definition.parameters))
            val json = snapshot(definition)
            RegisteredAgentTool(definition, json, AgentJson.digest(json), tool)
        }
        require(registered.map { it.definition.toolId }
            .distinct().size == registered.size) { "Duplicate tool id" }
        require(registered.map { it.definition.name }
            .distinct().size == registered.size) { "Duplicate tool function name" }
        return registered
    }

    companion object {
        private const val MAX_CACHED_VERSIONS = 256

        /** 保存配置与注册共用完整快照边界，防止合法草稿在补入运行字段后使目录失效。 */
        internal fun snapshot(definition: AgentToolDefinition): String {
            // 冻结参数之前的校验仍保留，此入口也独立保护配置保存和恢复路径。
            ToolSchemaValidator.validateDefinition(definition.parameters)
            require(definition.toolId.matches(Regex("^[a-zA-Z0-9_.-]{1,128}$"))) { "Invalid tool id" }
            require(definition.name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]{0,63}$"))) { "Invalid tool function name" }
            require(definition.contractVersion > 0 && definition.parameters.type == ToolSchema.Type.Object) { "Invalid tool contract" }
            require(!definition.parallelSafe || definition.effect == ToolEffect.ReadOnly) { "Only deterministic reads can run in parallel" }
            require(!definition.writesMemory || definition.effect == ToolEffect.LocalWrite) { "Memory writes must be local" }
            // 完整投影参与大小检查，参数树、说明和配置指纹不能分别规避总预算。
            val snapshot = JsonObject().apply {
                addProperty("toolId", definition.toolId)
                addProperty("name", definition.name)
                addProperty("version", definition.contractVersion)
                addProperty("description", definition.description)
                addProperty("effect", definition.effect.name)
                addProperty("parallelSafe", definition.parallelSafe)
                addProperty("writesMemory", definition.writesMemory)
                if (definition.external) addProperty("external", true)
                if (definition.configurationDigest.isNotEmpty()) addProperty(
                    "configurationDigest",
                    definition.configurationDigest
                )
                add("parameters", ToolSchemaCodec.encode(definition.parameters))
            }
            val json = AgentJson.canonical(snapshot)
            require(json.toByteArray(Charsets.UTF_8).size <= ToolSchemaValidator.MAX_ARGUMENT_BYTES) { "Tool definition is too large" }
            return json
        }
    }

    /** 根据客户端稳定 ID 查找；未知工具不回退到模型提供的名称。 */
    fun find(toolId: String, digest: String? = null): RegisteredAgentTool? =
        mTools.firstOrNull { it.definition.toolId == toolId && (digest == null || it.digest == digest) }
            ?: digest?.let { mVersions[it]?.takeIf { tool -> tool.definition.toolId == toolId } }

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
