package me.kafuuneko.rpclient.feature.groupchat.model

/** 群聊成员列表的 UI 模型；[muted] 只控制发言资格，不代表移除角色卡。 */
data class GroupChatMemberItem(
    val id: Long,
    val name: String,
    val description: String,
    val muted: Boolean
)
