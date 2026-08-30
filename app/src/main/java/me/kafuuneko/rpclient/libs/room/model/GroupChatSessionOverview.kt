package me.kafuuneko.rpclient.libs.room.model

/** 群聊列表专用投影，避免为预览加载完整成员对象和消息历史。 */
data class GroupChatSessionOverview(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long,
    /** 参与当前群聊的成员名称列表。 */
    val memberNames: String,
    /** 用于列表预览的最近一条消息内容。 */
    val latestMessageContent: String?,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int
)
