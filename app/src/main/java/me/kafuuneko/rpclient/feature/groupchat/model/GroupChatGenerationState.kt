package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊单轮或自动多轮成员生成的进度状态。 */
sealed class GroupChatGenerationState {
    data object Idle : GroupChatGenerationState()
    /** 正在生成第 [current] 个成员回复，共 [total] 个。 */
    data class Generating(
        val speakerName: String,
        val current: Int,
        val total: Int
    ) : GroupChatGenerationState()
    data class Failed(val message: String) : GroupChatGenerationState()
}
