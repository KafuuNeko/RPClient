package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

/** 单聊消息由创建时间与 ID 组成的稳定分页游标。 */
internal data class ChatMessageCursor(
    val createTime: Long,
    val messageId: Long
)

/** 已完成展示转换、可直接合并进 UiState 的一页单聊消息。 */
internal data class LoadedChatMessagePage(
    val items: List<ChatMessageUiModel>,
    val cursor: ChatMessageCursor?,
    val canLoadOlderMessages: Boolean,
    val totalMessageCount: Int
)

/** 将持久化消息转换为向前分页使用的稳定游标。 */
internal fun ChatMessage.toChatMessageCursor(): ChatMessageCursor {
    return ChatMessageCursor(createTime = createTime, messageId = id)
}
