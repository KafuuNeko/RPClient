package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊页面中的世界书分组及其会话启用状态。 */
data class GroupChatLorebookGroupItem(
    val lorebookId: Long,
    val lorebookName: String,
    val entries: List<GroupChatLorebookEntryItem>
) {
    val enabledCount: Int
        get() = entries.count { it.enabled }

    val totalCount: Int
        get() = entries.size

    val isAllEnabled: Boolean
        get() = totalCount > 0 && enabledCount == totalCount
}

/** 群聊页面中的世界书条目展示数据。 */
data class GroupChatLorebookEntryItem(
    val id: Long,
    val lorebookId: Long,
    val lorebookName: String,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val secondaryKeywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int,
    val enabled: Boolean
)

data class GroupChatAvailableCharacterItem(
    val id: Long,
    val name: String,
    val alreadyMember: Boolean
)
