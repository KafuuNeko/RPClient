package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage

/** 群聊消息由创建时间与 ID 组成的稳定分页游标。 */
internal data class GroupChatMessageCursor(
    val createTime: Long,
    val messageId: Long
)

/** 已完成展示转换、可直接合并进 UiState 的一页群聊消息。 */
internal data class LoadedGroupChatMessagePage(
    val items: List<GroupChatMessageItem>,
    val cursor: GroupChatMessageCursor?,
    val canLoadOlderMessages: Boolean
)

/** 将持久化群聊消息转换为向前分页使用的稳定游标。 */
internal fun GroupChatMessage.toGroupChatMessageCursor(): GroupChatMessageCursor {
    return GroupChatMessageCursor(createTime = createTime, messageId = id)
}
