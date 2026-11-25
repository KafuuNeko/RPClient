package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.groupchat.GroupChatGenerationMode
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData

/** 只保存失败批次尚未完成的角色，模式、触发消息与首位原位替换目标作为同一快照更新。 */
internal data class ReplyRetryContext(
    val sessionId: Long,
    val speakers: List<GroupChatMemberData>,
    val generationMode: GroupChatGenerationMode,
    val persona: UserPersona,
    val triggerUserMessageId: Long?,
    /** 仅作用于当前队列首位角色的原位覆盖目标，完成后推进后续角色时清空。 */
    val replacingReply: GroupChatMessage? = null,
    /** 标记 [replacingReply] 来自上次流式中断产生的残缺回复，与设置页重生成区分自动续聊规则。 */
    val isPartialReplacement: Boolean = false,
)
