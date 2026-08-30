package me.kafuuneko.rpclient.libs.room.model

/** 单聊列表专用投影，在一次查询中返回会话及其消息概览。 */
data class ChatSessionOverview(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 关联角色的唯一 ID。 */
    val characterId: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long,
    /** 用于列表预览的最近一条消息内容。 */
    val latestMessageContent: String?,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int
)
