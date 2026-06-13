package me.kafuuneko.rpclient.feature.chat.model

/** 单聊世界书选择器中的分组展示模型。 */
data class ChatLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val enabledCount: Int,
    val totalCount: Int,
    val entries: List<ChatLorebookEntryItem>
) {
    /** 分组内是否存在条目且全部已为当前会话启用。 */
    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}
