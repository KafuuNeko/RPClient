package me.kafuuneko.rpclient.feature.main.model.items

import me.kafuuneko.rpclient.libs.room.model.ChatSessionOverview
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 首页最近单聊会话卡片所需的扁平展示数据。 */
data class MainChatSessionItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 关联角色的唯一 ID。 */
    val characterId: String,
    /** 关联角色的显示名称快照。 */
    val characterName: String,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 当前会话或分组包含的消息数量。 */
    val messageCount: Int,
    /** 记录最近一次更新的时间戳，单位为毫秒。 */
    val updatedAt: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    override val latestTime: Long
) : MainHomeContentItem

/**
 * 将单聊概览转换为首页列表项，由调用方解析需要本地化的文本。
 *
 * @param characterName 角色名称，角色缺失时使用已本地化的占位文本
 * @param preview 已解析正文或图片、空会话占位符的消息预览
 * @return 首页单聊列表展示项
 */
internal fun ChatSessionOverview.toUiModel(
    characterName: String,
    preview: String
): MainChatSessionItem {
    return MainChatSessionItem(
        id = id.toString(),
        characterId = characterId.toString(),
        characterName = characterName,
        title = title,
        preview = preview,
        messageCount = messageCount,
        updatedAt = latestTime.formatTimestamp("MM-dd HH:mm"),
        latestTime = latestTime
    )
}
