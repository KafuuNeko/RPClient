package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 故事中的可选分卷；删除故事时分卷随之级联清理。 */
@Entity(
    tableName = "story_volumes",
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["storyId", "sortOrder"])
    ]
)
data class StoryVolume(
    // 分卷 ID。
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    // 所属故事 ID；故事删除时分卷级联删除。
    val storyId: Long,
    // 分卷标题。
    val title: String,
    // 分卷在故事中的排列顺序，数值越小越靠前。
    val sortOrder: Int
)
