package me.kafuuneko.rpclient.libs.room.model

/** Story 列表专用聚合投影，避免列表查询载入任一章节的完整私密正文。 */
data class StoryOverview(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 正文内容包含的字符数量。 */
    val contentCharacterCount: Int,
    /** 经过长度限制、供列表快速浏览的内容预览。 */
    val preview: String,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long
)
