package me.kafuuneko.rpclient.libs.model


// TODO: 本页面的所有Model仅作为测试使用，实际接入实际数据或者实际业务逻辑后，不应该继续使用本页的数据模型，而应该使用实际的数据类型
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

