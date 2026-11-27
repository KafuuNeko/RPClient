package me.kafuuneko.rpclient.libs.agent.tool.builtin.memory

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.page
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.libs.memory.MemoryStatus

/** search_memory 的领域读取实现，结果与重试由统一执行器管理。 */
class SearchMemoryTool(config: EffectiveToolConfig, private val mMemory: MemoryWriteService) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        val query = arguments["query"].asString
        val items = mMemory.memoryView(context).filter {
            it["status"].asString == MemoryStatus.Active.name && it["body"].asString.contains(
                query,
                true
            )
        }.map { entry ->
            entry.deepCopy()
                .apply { addProperty("excerpt", remove("body").asString.take(180)) }
        }
        return page(items, arguments, context)
    }
}
