package me.kafuuneko.rpclient.libs.agent.tool.builtin.memory

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.base.AbstractLocalWriteTool
import me.kafuuneko.rpclient.libs.agent.tool.base.LocalChangePlan
import me.kafuuneko.rpclient.libs.agent.tool.catalog.EffectiveToolConfig

/** invalidate_memory 工具，组合记忆领域服务并保持统一授权提交边界。 */
class InvalidateMemoryTool(config: EffectiveToolConfig, private val mMemory: MemoryWriteService) :
    AbstractLocalWriteTool(config) {
    /** @return 记忆领域校验后的写入或延后提案计划。 */
    override suspend fun planChange(
        context: ToolExecutionContext,
        arguments: JsonObject
    ): LocalChangePlan =
        mMemory.planChange(context, arguments, MemoryOperation.Invalidate)
}
