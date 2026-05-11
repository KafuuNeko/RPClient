package me.kafuuneko.rpclient.feature.chatcreate.model

data class ChatCreateLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entryCount: Int,
    val entries: List<ChatCreateLorebookEntryItem>
)
