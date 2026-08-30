package me.kafuuneko.rpclient.libs.room.model

/** 章节大纲专用投影，只读取标题、顺序和轻量统计，不载入正文。 */
data class StoryChapterOverview(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 当前操作关联的故事 ID。 */
    val storyId: Long,
    /** 当前操作关联的故事卷 ID。 */
    val volumeId: Long?,
    /** 供界面展示或持久化的标题。 */
    val title: String,
    /** 正文内容包含的字符数量。 */
    val contentCharacterCount: Int,
    /** 当前对象用于稳定排序的顺序值。 */
    val sortOrder: Int,
    /** 故事正文用于防止并发覆盖的修订版本。 */
    val contentRevision: Long,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long
)
