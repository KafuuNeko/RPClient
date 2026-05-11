package me.kafuuneko.rpclient.feature.chat.model

data class ChatSessionItem(
    val id: Long,
    val title: String,
    val summarize: String,
    val userNote: String,
    val creatorNotes: String,
    val messageCount: Int,
    val enabledLorebookEntryIds: Set<Long>
)

