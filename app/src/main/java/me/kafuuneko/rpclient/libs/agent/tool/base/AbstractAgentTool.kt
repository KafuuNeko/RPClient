package me.kafuuneko.rpclient.libs.agent.tool.base

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchemaCodec
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/**
 * 无调用状态的工具模板，固定参数隔离、可用性检查和业务规划顺序。
 * - 构造期冻结契约与默认值，不调用子类成员。
 * - prepare 不派发副作用，原始入参指纹始终由执行器维护。
 */
abstract class AbstractAgentTool(
    config: EffectiveToolConfig,
    private val mAvailability: ToolAvailability = ToolAvailability.Always,
) : AgentTool {
    final override val definition: AgentToolDefinition = config.definition.copy(
        parameters = ToolSchemaCodec.decode(
            ToolSchemaCodec.encode(config.definition.parameters).toString()
        ),
    )
    private val mDefaults = config.defaults.deepCopy()

    /** 缺配置保留可恢复语义，只有撤权阻止旧结果回放。 */
    final override suspend fun isEnabled(): Boolean =
        mAvailability.state() != ToolAvailabilityState.Revoked

    /** @return 本次独立动作计划，不修改调用者参数或共享配置。 */
    final override suspend fun prepare(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction {
        // 每次调用独立复制，显式 null 和显式参数均覆盖冻结默认值。
        val effective = mDefaults.deepCopy()
        arguments.entrySet().forEach { (key, value) -> effective.add(key, value.deepCopy()) }
        when (mAvailability.state()) {
            ToolAvailabilityState.Revoked -> throw ToolAuthorizationRevokedException()
            ToolAvailabilityState.NeedsConfiguration -> throw ToolPreparationException(ToolErrorCode.HTTP_NEEDS_CONFIGURATION)
            ToolAvailabilityState.Ready -> Unit
        }
        validateBusinessInput(context, effective)
        return createAction(context, effective)
    }

    /** 校验领域前置条件；无额外规则的读取工具可直接使用默认实现。 */
    protected open suspend fun validateBusinessInput(
        context: ToolExecutionContext,
        arguments: JsonObject
    ) = Unit

    /** @return 延后执行的动作，禁止在此进行外发或持久化写入。 */
    protected abstract suspend fun createAction(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction
}
