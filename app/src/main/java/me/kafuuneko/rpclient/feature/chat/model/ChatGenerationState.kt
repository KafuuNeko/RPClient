package me.kafuuneko.rpclient.feature.chat.model

sealed class ChatGenerationState {
    data object Idle : ChatGenerationState()
    data object Requesting : ChatGenerationState()
    data class Streaming(
        val messageId: Long?,
        val content: String
    ) : ChatGenerationState()
    data class Failed(val message: String) : ChatGenerationState()
}

