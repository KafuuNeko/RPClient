package me.kafuuneko.rpclient.libs.agent.tool.builtin.math

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.builtin.DiceNotation
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig
import me.kafuuneko.rpclient.utils.jsonObject

/** roll_dice 的领域读取实现，结果与重试由统一执行器管理。 */
class RollDiceTool(config: EffectiveToolConfig) : AbstractReadTool(config) {
    /** @return 当前授权范围内的有界结果。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult {
        val result = DiceNotation.roll(arguments["notation"].asString)
        return ToolResult.Success(
            jsonObject(
                "rolls" to JsonArray().apply { result.rolls.forEach(::add) },
                "kept" to JsonArray().apply { result.kept.forEach(::add) },
                "modifier" to result.modifier,
                "total" to result.total
            )
        )
    }
}
