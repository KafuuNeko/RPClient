package me.kafuuneko.rpclient.libs.agent.tool.base

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 不含会话身份的外部请求计划，凭据只在派发时读取。 */
data class ExternalRequestPlan(
    val destinationHost: String,
    val parameterSummary: JsonObject,
    val execute: suspend () -> ToolResult,
)

/** 外部家族按冻结执行性质包装动作，确认与重试始终由执行器决定。 */
abstract class AbstractExternalTool(config: EffectiveToolConfig, availability: ToolAvailability) :
    AbstractAgentTool(config, availability) {
    /** @return 明确区分外发读取与外部写入的动作。 */
    final override suspend fun createAction(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction {
        // 先规划无会话上下文的请求，再严格按冻结执行性质封装对应外发动作。
        val plan = planRequest(arguments)
        return when (definition.effect) {
            ToolEffect.ReadOnly -> ToolAction.ExternalRead(
                plan.destinationHost,
                plan.parameterSummary,
                plan.execute
            )

            ToolEffect.ExternalWrite -> ToolAction.ExternalWrite(
                plan.destinationHost,
                plan.parameterSummary,
                plan.execute
            )

            else -> error("Invalid external tool effect")
        }
    }

    /** @return 无上下文请求规划，禁止在准备阶段派发 HTTP。 */
    protected abstract suspend fun planRequest(arguments: JsonObject): ExternalRequestPlan
}
