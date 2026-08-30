package me.kafuuneko.rpclient.feature.chatcreate.model

/** 新建单聊页面中的世界书分组。 */
data class ChatCreateLorebookGroupItem(
    /** 关联世界书的唯一 ID。 */
    val lorebookId: Long,
    /** 关联世界书的显示名称。 */
    val lorebookName: String,
    /** 当前世界书或分组包含的条目数量。 */
    val entryCount: Int,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<ChatCreateLorebookEntryItem>
)
