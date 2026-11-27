package me.kafuuneko.rpclient.libs.agent.tool.builtin.memory

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.textPage
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** read_memory 的领域读取实现，结果与重试由统一执行器管理。 */
class ReadMemoryTool(config: EffectiveToolConfig, private val mMemory: MemoryWriteService) :
    AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        // 优先按提案 ID 定位未提交预览，否则按表与主键匹配可见记忆。
        val entry = mMemory.memoryView(context).firstOrNull {
            if (arguments.has("proposalId")) it["proposalId"] == arguments["proposalId"]
            else arguments.has("memoryId") && arguments.has("table") && it["memoryId"] == arguments["memoryId"] && it["table"] == arguments["table"]
        } ?: return ToolResult.RecoverableError(ToolErrorCode.MEMORY_NOT_VISIBLE)
        // 按固定字符窗口分页返回正文，并附带当前提案与状态标记。
        val page = textPage(
            entry["body"].asString,
            arguments["cursor"]?.asInt ?: 0,
            "revision",
            entry["revision"].asLong
        )
        return page.copy(data = page.data.apply {
            add(
                "proposed",
                entry["proposed"]
            )
            add("status", entry["status"])
        })
    }
}
