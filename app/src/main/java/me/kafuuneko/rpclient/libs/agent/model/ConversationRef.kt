package me.kafuuneko.rpclient.libs.agent.model

/** 单聊与群聊使用不同身份域，防止相同数字 ID 串用运行与工具权限。 */
sealed class ConversationRef(val id: Long, val key: String) {
    init {
        require(id > 0) { "Invalid conversation id" }
    }

    data class Single(val chatId: Long) : ConversationRef(chatId, "single/$chatId")
    data class Group(val groupId: Long) : ConversationRef(groupId, "group/$groupId")
}

/** 每次写入都需复核的运行资格；历史或设置变化后旧令牌失效。 */
data class AgentExecutionToken(
    val conversation: ConversationRef,
    val runId: String,
    val settingsVersion: Long,
    val historyVersion: Long,
    val executionEpoch: Long,
)

/** 持久化运行状态；等待用户属于可恢复挂起，不能当作完成释放运行归属。 */
enum class AgentRunState {
    Preparing, RequestingModel, ExecutingTools, WaitingForUser, Committing, Cancelling,
    Completed, Failed, Cancelled, Interrupted, Expired;

    val isTerminal: Boolean
        get() = this in setOf(Completed, Failed, Cancelled, Interrupted, Expired)
}

/** 调用结果与正文提交分别记录，崩溃后的不确定副作用禁止自动重放。 */
enum class ToolInvocationState {
    Prepared, Dispatching, AwaitingUser, Completed, Failed, UnknownOutcome
}
