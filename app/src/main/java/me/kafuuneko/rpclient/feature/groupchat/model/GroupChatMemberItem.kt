package me.kafuuneko.rpclient.feature.groupchat.model

data class GroupChatMemberItem(
    val id: Long,
    val name: String,
    val description: String,
    val muted: Boolean
)
