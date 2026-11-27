package me.kafuuneko.rpclient.libs.agent.tool.catalog

import androidx.annotation.StringRes
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.builtin.memory.MemoryWriteService
import me.kafuuneko.rpclient.libs.agent.tool.builtin.scene.SceneStateService
import me.kafuuneko.rpclient.libs.agent.tool.proposal.LocalProposalHandler
import me.kafuuneko.rpclient.libs.prompt.tool.ToolPromptSpec
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository

/** 共享领域分组，页面之间不互相引用展示模型。 */
enum class ToolGroup { Lore, Chat, Memory, Scene, Utility, Interaction, Network }

/** 可编辑默认参数规格，实际值必须通过对应 Schema 的完整校验。 */
data class EditableDefaultSpec(
    val key: String,
    val defaultValue: JsonElement,
    @param:StringRes val labelRes: Int
)

/** 工具稳定身份和执行契约；展示名称、语言和排序不进入执行指纹。 */
data class ToolSpecification(
    val toolId: String,
    val prompt: ToolPromptSpec,
    val group: ToolGroup,
    val effect: ToolEffect = ToolEffect.ReadOnly,
    val writesMemory: Boolean = false,
    val defaultEnabled: Boolean = true,
    val editableDefaults: List<EditableDefaultSpec> = emptyList(),
) {
    /** @return 保持既有版本、模型函数名及执行性质的原始契约。 */
    fun definition(): AgentToolDefinition = AgentToolDefinition(
        toolId = toolId, name = toolId, contractVersion = 1,
        description = prompt.description, parameters = prompt.parameters,
        effect = effect, parallelSafe = effect == ToolEffect.ReadOnly, writesMemory = writesMemory,
    )
}

/** 工厂输入的冻结投影；工具基类会再次隔离可变 JSON，避免共享调用参数。 */
data class EffectiveToolConfig(
    val definition: AgentToolDefinition,
    val defaults: JsonObject = JsonObject()
)

/** 运行工厂的稳定领域依赖，不含当前会话、参数或凭据。 */
data class ToolServices(
    val data: AgentLocalDataRepository,
    val memory: MemoryWriteService,
    val scene: SceneStateService
)

/** 通过构造函数将冻结配置交给业务工具，不增加运行期装饰层。 */
fun interface ToolFactory {
    /** @return 新配置版本对应的无调用状态工具实例。 */
    fun create(config: EffectiveToolConfig, services: ToolServices): AgentTool
}

/** 单次注册关联规格、确定性排序、展示资源及业务工厂。 */
data class ToolRegistration(
    val specification: ToolSpecification,
    val order: Int,
    @param:StringRes val labelRes: Int,
    val factory: ToolFactory,
    val proposalHandler: ((ToolServices) -> LocalProposalHandler)? = null,
)
