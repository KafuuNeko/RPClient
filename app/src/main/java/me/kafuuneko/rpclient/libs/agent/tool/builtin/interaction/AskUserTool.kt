package me.kafuuneko.rpclient.libs.agent.tool.builtin.interaction

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractAgentTool
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 普通提问仅提供输入，不能被当作外发或本地写入确认。 */
class AskUserTool(config: EffectiveToolConfig) : AbstractAgentTool(config) {
    /** @return 立即释放执行栈的等待动作，不持有等待协程。 */
    override suspend fun createAction(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): ToolAction =
        ToolAction.AwaitingUser(
            ToolResult.AwaitingUser(
                arguments["question"].asString,
                arguments.getAsJsonArray("options")?.map { it.asString }.orEmpty()
            )
        )
}
