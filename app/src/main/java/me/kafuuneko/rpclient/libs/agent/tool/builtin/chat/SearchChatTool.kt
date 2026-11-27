package me.kafuuneko.rpclient.libs.agent.tool.builtin.chat

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository

/** search_chat 的领域读取实现，结果与重试由统一执行器管理。 */
class SearchChatTool(config: EffectiveToolConfig, private val mData: AgentLocalDataRepository) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        return mData.searchChat(
            context,
            arguments["query"].asString,
            arguments["cursor"]?.asString?.let { AgentJson.parse(it).asJsonObject })
    }
}
