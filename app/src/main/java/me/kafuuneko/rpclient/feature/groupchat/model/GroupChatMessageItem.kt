package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage

/** 群聊消息展示模型，保留说话者快照以避免角色改名影响历史归属。 */
data class GroupChatMessageItem(
    val id: Long,
    val source: GroupChatMessage.Source,
    val speakerName: String,
    val content: String,
    val time: String,
    val isStreaming: Boolean = false
)
