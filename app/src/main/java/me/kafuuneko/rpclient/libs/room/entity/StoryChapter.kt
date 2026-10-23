package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 故事章节正文与续写引导的最小加载、保存和乐观锁单元。
 *
 * [volumeId] 为空表示未分卷；删除分卷时数据库只解除归属，Repository 负责在删除前重排，
 * 从而保证正文不会随分卷误删。
 */
@Entity(
    tableName = "story_chapters",
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StoryVolume::class,
            parentColumns = ["id"],
            childColumns = ["volumeId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [
        Index(value = ["storyId", "volumeId", "sortOrder"]),
        Index("volumeId")
    ]
)
data class StoryChapter(
    // 章节 ID。
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    // 所属故事 ID；故事删除时章节级联删除。
    val storyId: Long,
    // 所属分卷 ID；为空表示章节尚未归入分卷。
    val volumeId: Long? = null,
    // 章节标题。
    val title: String,
    // 章节正文内容。
    val content: String = "",
    // 当前章节后续普通续写持续使用的编写引导。
    @ColumnInfo(defaultValue = "''")
    val continuationGuidance: String = "",
    // 章节在当前分组中的排列顺序，数值越小越靠前。
    val sortOrder: Int,
    // 章节可编辑内容的乐观锁版本号，正文或续写引导成功写入后递增。
    val contentRevision: Long = 0L,
    // 章节创建时间。
    val createTime: Long,
    // 章节最近修改时间。
    val latestTime: Long
)
