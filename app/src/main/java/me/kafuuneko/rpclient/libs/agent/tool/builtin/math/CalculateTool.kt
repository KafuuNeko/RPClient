package me.kafuuneko.rpclient.libs.agent.tool.builtin.math

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.RestrictedCalculator
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.utils.jsonObject

/** calculate 的领域读取实现，结果与重试由统一执行器管理。 */
class CalculateTool(config: EffectiveToolConfig) : AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        return ToolResult.Success(jsonObject("value" to RestrictedCalculator.calculate(arguments["expression"].asString)))
    }
}
