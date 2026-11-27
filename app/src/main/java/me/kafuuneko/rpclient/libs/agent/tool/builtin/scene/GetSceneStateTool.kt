package me.kafuuneko.rpclient.libs.agent.tool.builtin.scene

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractReadTool
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 读取当前剧情与本轮提案叠加预览，不直接访问 Room。 */
class GetSceneStateTool(config: EffectiveToolConfig, private val mScene: SceneStateService) :
    AbstractReadTool(config) {
    /** @return 带乐观修订号的剧情预览。 */
    override suspend fun read(context: ToolExecutionContext, arguments: JsonObject): ToolResult =
        ToolResult.Success(mScene.scene(context))
}
