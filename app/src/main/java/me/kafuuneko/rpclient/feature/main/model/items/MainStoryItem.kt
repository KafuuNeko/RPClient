package me.kafuuneko.rpclient.feature.main.model.items

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
