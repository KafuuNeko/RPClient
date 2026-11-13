package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 故事级写作设置与全局生成状态。
 *
 * 正文由 StoryChapter 分章保存；[revision] 是跨章节、结构、设置和世界书时序状态共享的
 * 单调版本号，用于阻止基于旧 Story 快照构建的生成结果覆盖新状态。
 */
@Entity(tableName = "stories", indices = [Index("userPersonaId")])
data class Story(
    // 故事 ID。
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    // 故事标题。
    val title: String,
    // 跨章节长期记忆，用于生成当前章节时补充全局上下文。
    val memory: String = "",
    // 故事级滚动摘要，用于压缩已完成内容的上下文。
    val summary: String = "",
    // 作者备注，用于向模型补充当前故事的写作要求。
    val authorNote: String = "",
    // 绑定的人设 ID；-1 表示不向故事注入人设。
    @ColumnInfo(defaultValue = "-1")
    val userPersonaId: Long = UserPersona.NONE,
    // 世界书时序状态当前推进到的生成步数。
    val worldInfoGenerationStep: Int = 0,
    // 故事结构、设置和全局生成状态共享的乐观锁版本号。
    val revision: Long = 0L,
    // 故事创建时间。
    val createTime: Long,
    // 故事最近修改时间。
    val latestTime: Long
)
