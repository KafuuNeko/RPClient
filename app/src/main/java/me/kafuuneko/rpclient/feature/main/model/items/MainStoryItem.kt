package me.kafuuneko.rpclient.feature.main.model.items

import me.kafuuneko.rpclient.libs.room.model.StoryOverview
import me.kafuuneko.rpclient.utils.formatTimestamp

/** 首页故事卡片所需的元数据，不包含完整正文。 */
data class MainStoryItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 正文内容包含的字符数量。 */
    val contentCharacterCount: Int,
    /** 记录最近一次更新的时间戳，单位为毫秒。 */
    val updatedAt: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    override val latestTime: Long
) : MainHomeContentItem

/** 将故事概览转换为首页列表项，保留原有空白归一化规则。 */
internal fun StoryOverview.toUiModel(): MainStoryItem {
    return MainStoryItem(
        id = id,
        title = title,
        preview = preview.replace(WHITESPACE_REGEX, " ").trim(),
        contentCharacterCount = contentCharacterCount,
        updatedAt = latestTime.formatTimestamp("MM-dd HH:mm"),
        latestTime = latestTime
    )
}

/** 连续空白字符匹配正则，用于故事内容摘要的空白压缩与规整。 */
private val WHITESPACE_REGEX = Regex("\\s+")
