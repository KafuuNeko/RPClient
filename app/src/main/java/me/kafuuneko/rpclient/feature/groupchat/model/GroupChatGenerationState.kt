package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.model.ChatWritingState

/** 群聊单轮或自动多轮成员生成的进度状态。 */
sealed class GroupChatGenerationState {
    data object Idle : GroupChatGenerationState()

    /** 正在生成第 [current] 个成员回复，共 [total] 个。 */
    data class Generating(
        /** 当前发言者的显示名称快照。 */
        val speakerName: String,
        /** 当前已生成或处理的数量。 */
        val current: Int,
        /** 当前流程需要处理的总数量。 */
        val total: Int
    ) : GroupChatGenerationState()

    /** 代写期间互斥展示输入框状态或尚未落库的用户消息。 */
    data class Impersonating(
        val writing: ChatWritingState? = null,
        val preview: GroupChatMessageItem? = null,
    ) : GroupChatGenerationState()

    data class Failed(val message: String, val canRetryReply: Boolean = false) :
        GroupChatGenerationState()
}
