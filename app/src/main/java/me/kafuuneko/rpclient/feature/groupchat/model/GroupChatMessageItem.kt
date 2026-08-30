package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.model.toMessageContentParts
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource

/** 群聊消息展示模型，保留说话者快照以避免角色改名影响历史归属。 */
data class GroupChatMessageItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 产生当前数据的来源。 */
    val source: GroupChatMessageSource,
    /** 当前发言者的显示名称快照。 */
    val speakerName: String,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 按正文和推理块拆分后的消息展示片段。 */
    val parts: List<MessageContentPart> = content.toMessageContentParts(id.toString()),
    /** 当前记录对应的时间戳。 */
    val time: String,
    /** 当前消息或请求是否处于流式生成状态。 */
    val isStreaming: Boolean = false
)
