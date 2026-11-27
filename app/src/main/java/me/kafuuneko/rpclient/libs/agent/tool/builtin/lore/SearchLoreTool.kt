package me.kafuuneko.rpclient.libs.agent.tool.builtin.lore

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.page
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.utils.jsonObject

/** search_lore 的领域读取实现，结果与重试由统一执行器管理。 */
class SearchLoreTool(config: EffectiveToolConfig, private val mData: AgentLocalDataRepository) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        val query = arguments["query"].asString
        val entries = mData.lore(context).filter {
            it.name.contains(query, true) || it.content.contains(
                query,
                true
            ) || it.keywords.contains(query, true)
        }
        return page(entries.map {
            jsonObject(
                "id" to it.id,
                "title" to it.name.take(100),
                "excerpt" to it.content.take(150)
            )
        }, arguments, context)
    }
}
