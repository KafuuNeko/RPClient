package me.kafuuneko.rpclient.libs.agent.tool.builtin.lore

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.textPage
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository

/** read_lore_entry 的领域读取实现，结果与重试由统一执行器管理。 */
class ReadLoreEntryTool(config: EffectiveToolConfig, private val mData: AgentLocalDataRepository) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        val entry = mData.lore(context).firstOrNull { it.id == arguments["id"].asLong }
            ?: return ToolResult.RecoverableError(ToolErrorCode.LORE_NOT_VISIBLE)
        return textPage(entry.content, arguments["cursor"]?.asInt ?: 0, "id", entry.id)
    }
}
