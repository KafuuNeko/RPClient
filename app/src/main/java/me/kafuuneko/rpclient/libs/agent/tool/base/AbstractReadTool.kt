package me.kafuuneko.rpclient.libs.agent.tool.base

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 读取家族只包装延后动作，确定性和并发许可仍由冻结契约声明。 */
abstract class AbstractReadTool(config: EffectiveToolConfig) : AbstractAgentTool(config) {
    /** @return 由执行器派发并保存结果的读取动作。 */
    final override suspend fun createAction(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction =
        ToolAction.Read { ToolErrorMapper.read { read(context, arguments) } }

    /** @return 本次读取或计算结果，不保存任何当前调用状态。 */
    protected abstract suspend fun read(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolResult
}
