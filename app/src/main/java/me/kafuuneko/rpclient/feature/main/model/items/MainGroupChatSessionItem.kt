package me.kafuuneko.rpclient.feature.main.model.items

import me.kafuuneko.rpclient.libs.room.model.GroupChatSessionOverview
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 首页群聊会话卡片所需的扁平展示数据。 */
data class MainGroupChatSessionItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 参与当前群聊的成员名称列表。 */
    val memberNames: String,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int,
    /** 记录最近一次更新的时间戳，单位为毫秒。 */
    val updatedAt: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    override val latestTime: Long
) : MainHomeContentItem

/** 将群聊概览与已本地化的消息预览转换为首页列表项。 */
internal fun GroupChatSessionOverview.toUiModel(preview: String): MainGroupChatSessionItem {
    return MainGroupChatSessionItem(
        id = id.toString(),
        title = title,
        memberNames = memberNames,
        preview = preview,
        messageCount = messageCount,
        updatedAt = latestTime.formatTimestamp("MM-dd HH:mm"),
        latestTime = latestTime,
    )
}
