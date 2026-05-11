package me.kafuuneko.rpclient.feature.chat.model

data class ChatMessageUiModel(
    val id: String,
    val role: MessageRole,
    val speaker: String,
    val content: String,
    val parts: List<ChatMessageContentPart>,
    val time: String,
    val tokenCount: Int,
    val isStreaming: Boolean = false
)

sealed class ChatMessageContentPart {
    data class Text(val content: String) : ChatMessageContentPart()
    data class Think(val id: String, val content: String) : ChatMessageContentPart()
}
