package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage

data class GroupChatMessageItem(
    val id: Long,
    val source: GroupChatMessage.Source,
    val speakerName: String,
    val content: String,
    val time: String,
    val isStreaming: Boolean = false
)
