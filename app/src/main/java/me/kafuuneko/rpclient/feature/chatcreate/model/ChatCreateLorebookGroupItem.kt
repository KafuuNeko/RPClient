package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页面中的世界书分组。 */
data class ChatCreateLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entryCount: Int,
    val entries: List<ChatCreateLorebookEntryItem>
)
