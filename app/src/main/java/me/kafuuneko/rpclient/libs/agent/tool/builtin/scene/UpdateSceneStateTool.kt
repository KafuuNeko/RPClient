package me.kafuuneko.rpclient.libs.agent.tool.builtin.scene

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractLocalWriteTool
import me.kafuuneko.rpclient.libs.agent.tool.base.LocalChangePlan
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** 剧情增量写入工具，准备及提交规则由剧情服务统一维护。 */
class UpdateSceneStateTool(config: EffectiveToolConfig, private val mScene: SceneStateService) :
    AbstractLocalWriteTool(config) {
    /** @return 经证据与修订校验的剧情变更计划。 */
    override suspend fun planChange(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): LocalChangePlan = mScene.planChange(context, arguments)
}
