package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.groupchat.GroupChatGenerationMode
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/** 只保存失败批次尚未完成的角色，模式与触发消息作为同一快照更新。 */
internal data class ReplyRetryContext(
    val sessionId: Long,
    val speakers: List<GroupChatMemberData>,
    val generationMode: GroupChatGenerationMode,
    val triggerUserMessageId: Long?
)
