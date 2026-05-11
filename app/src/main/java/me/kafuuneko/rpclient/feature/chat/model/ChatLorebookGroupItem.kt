package me.kafuuneko.rpclient.feature.chat.model

data class ChatLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val enabledCount: Int,
    val totalCount: Int,
    val entries: List<ChatLorebookEntryItem>
) {
    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}
