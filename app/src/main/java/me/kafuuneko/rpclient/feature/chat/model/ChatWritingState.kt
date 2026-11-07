package me.kafuuneko.rpclient.feature.chat.model

/** 仅用于生成期间的输入区状态；完整推理在 ViewModel 私有快照中保存。 */
data class ChatWritingState(
    val phase: ChatWritingPhase,
    val hasReasoning: Boolean = false,
    val reasoningExpanded: Boolean = false,
    val reasoningDetail: String = "",
)

/** 代写进行中的阶段，结束后整个状态节点移除。 */
enum class ChatWritingPhase {
    Requesting,
    Thinking,
    Writing,
}
