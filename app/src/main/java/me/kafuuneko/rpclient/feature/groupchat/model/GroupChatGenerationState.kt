package me.kafuuneko.rpclient.feature.groupchat.model

sealed class GroupChatGenerationState {
    data object Idle : GroupChatGenerationState()
    data class Generating(
        val speakerName: String,
        val current: Int,
        val total: Int
    ) : GroupChatGenerationState()
    data class Failed(val message: String) : GroupChatGenerationState()
}
