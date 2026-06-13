package me.kafuuneko.rpclient.feature.chat.model

/** 单聊页面使用的会话设置快照，不直接暴露 Room 实体。 */
data class ChatSessionItem(
    val id: Long,
    val title: String,
    val summarize: String,
    val userNote: String,
    val userName: String,
    val userDescription: String,
    val creatorNotes: String,
    val autoSummaryPaused: Boolean,
    val messageCount: Int,
    val enabledLorebookEntryIds: Set<Long>
)
