package me.kafuuneko.rpclient.model

import me.kafuuneko.rpclient.libs.chat.UserWritingProgress

/** 仅用于生成期间的输入区状态；完整推理在 ViewModel 私有快照中保存。 */
data class ChatWritingState(
    val phase: ChatWritingPhase = ChatWritingPhase.Requesting,
    val hasReasoning: Boolean = false,
    val reasoningExpanded: Boolean = false,
    val reasoningDetail: String = "",
) {
    /** 从累计输出更新阶段，仅在用户展开时将推理正文交给界面。 */
    fun withProgress(progress: UserWritingProgress): ChatWritingState = copy(
        phase = when {
            progress.body.isNotBlank() -> ChatWritingPhase.Writing
            progress.reasoning.isNotBlank() -> ChatWritingPhase.Thinking
            else -> ChatWritingPhase.Requesting
        },
        hasReasoning = progress.reasoning.isNotBlank(),
        reasoningDetail = if (reasoningExpanded) progress.reasoning else "",
    )

    /** 切换思考详情，同时从页面状态中移除已折叠的长文本。 */
    fun toggleReasoning(reasoning: String): ChatWritingState = copy(
        reasoningExpanded = !reasoningExpanded,
        reasoningDetail = if (reasoningExpanded) "" else reasoning,
    )
}

/** 代写进行中的阶段，结束后整个状态节点移除。 */
enum class ChatWritingPhase {
    Requesting,
    Thinking,
    Writing,
}
