package me.kafuuneko.rpclient.libs.agent.tool.builtin.chat

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.textPage
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository

/** read_messages 的领域读取实现，结果与重试由统一执行器管理。 */
class ReadMessagesTool(config: EffectiveToolConfig, private val mData: AgentLocalDataRepository) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        val messages = mData.messageWindow(
            context.token.conversation,
            arguments["message_id"].asLong,
            arguments["context"]?.asInt ?: 0
        )
        val text =
            messages.joinToString("\n\n") { "[${it.id} ${if (it.user) "user" else "character"}]\n${it.content}" }
        return textPage(
            text,
            arguments["cursor"]?.asInt ?: 0,
            "messageId",
            arguments["message_id"].asLong
        )
    }
}
