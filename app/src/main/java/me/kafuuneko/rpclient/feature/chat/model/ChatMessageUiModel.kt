package me.kafuuneko.rpclient.feature.chat.model

data class ChatMessageUiModel(
    val id: String,
    val role: MessageRole,
    val speaker: String,
    val content: String,
    val time: String,
    val tokenCount: Int,
    val isStreaming: Boolean = false
)