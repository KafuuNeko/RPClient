package me.kafuuneko.rpclient.libs.model

enum class MessageRole {
    User,
    Assistant,
    Narrator
}

data class RpCharacterUiModel(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val avatarText: String,
    val tags: List<String>,
    val sessions: Int,
    val updatedAt: String,
    val accentColor: Long
)

data class ChatSessionUiModel(
    val id: String,
    val characterName: String,
    val title: String,
    val preview: String,
    val messageCount: Int,
    val branchCount: Int,
    val updatedAt: String
)

data class ChatMessageUiModel(
    val id: String,
    val role: MessageRole,
    val speaker: String,
    val content: String,
    val time: String,
    val tokenCount: Int,
    val isStreaming: Boolean = false
)

data class ProviderUiModel(
    val id: String,
    val name: String,
    val type: String,
    val endpoint: String,
    val model: String,
    val status: String,
    val isEnabled: Boolean
)

data class LoreBookUiModel(
    val id: String,
    val title: String,
    val scope: String,
    val entries: Int,
    val enabled: Boolean,
    val updatedAt: String
)

data class LoreEntryUiModel(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val content: String,
    val priority: Int,
    val isEnabled: Boolean
)

