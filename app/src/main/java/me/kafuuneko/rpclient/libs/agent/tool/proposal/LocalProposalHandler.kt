package me.kafuuneko.rpclient.libs.agent.tool.proposal

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext

/** 领域提案的对称生命周期，持久化 operation 保持旧检查点兼容。 */
interface LocalProposalHandler {
    val operations: Set<String>

    /** @return 当前变更是否依赖本轮尚未提交的预览修订。 */
    suspend fun dependsOnPending(context: ToolExecutionContext, proposal: JsonObject): Boolean

    /** @return 短事务内是否成功提交；证据不支持或前序提案未提交时返回 false。 */
    suspend fun commit(
        context: ToolExecutionContext,
        proposal: JsonObject,
        finalMessageId: Long
    ): Boolean
}

/** 按稳定操作身份分派提案，不在执行器或基础数据仓库增加领域条件分支。 */
class LocalProposalRegistry(handlers: List<LocalProposalHandler>) {
    private val mHandlers = buildMap {
        handlers.distinct().forEach { handler ->
            handler.operations.forEach { operation ->
                require(put(operation, handler) == null) { "Duplicate proposal operation" }
            }
        }
    }

    /** @return 对应领域处理器的提交结果；未知操作拒绝静默忽略。 */
    suspend fun commit(
        context: ToolExecutionContext,
        proposal: JsonObject,
        finalMessageId: Long
    ): Boolean {
        val handler =
            requireNotNull(mHandlers[proposal["operation"].asString]) { "Unknown proposal operation" }
        return handler.commit(context, proposal, finalMessageId)
    }
}
