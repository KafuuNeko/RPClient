package me.kafuuneko.rpclient.libs.agent.tool.base

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 本地写入规划值；commit 只能在执行器或最终回复的授权短事务内执行。 */
data class LocalChangePlan(
    val result: ToolResult.Success,
    val confirmation: ToolResult.AwaitingUser? = null,
    val commit: suspend () -> Unit,
)

/** 将领域写入规划封装成统一动作，不自行开启事务或提交提案。 */
abstract class AbstractLocalWriteTool(config: EffectiveToolConfig) : AbstractAgentTool(config) {
    /** @return 保留确认、提案状态与短事务提交边界的动作。 */
    final override suspend fun createAction(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction {
        val plan = planChange(context, arguments)
        return ToolAction.LocalWrite(plan.result, plan.confirmation, plan.commit)
    }

    /** @return 已完成前置校验的变更计划。 */
    protected abstract suspend fun planChange(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): LocalChangePlan
}
