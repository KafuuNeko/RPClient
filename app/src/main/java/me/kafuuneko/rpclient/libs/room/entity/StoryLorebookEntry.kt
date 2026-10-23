package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Story 启用的世界书条目及条目级时序状态。
 *
 * 世界书的角色作用域由角色卡关联关系在 Prompt 构建时推断；四个时序字段全为空表示
 * 当前没有有效的 sticky/cooldown 状态。
 */
@Entity(
    tableName = "story_lorebook_entries",
    primaryKeys = ["storyId", "lorebookEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = Story::class,
            parentColumns = ["id"],
            childColumns = ["storyId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LorebookEntry::class,
            parentColumns = ["id"],
            childColumns = ["lorebookEntryId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("lorebookEntryId")
    ]
)
data class StoryLorebookEntry(
    // 所属故事 ID；故事删除时关联记录级联删除。
    val storyId: Long,
    // 关联世界书条目 ID；条目删除时关联记录级联删除。
    val lorebookEntryId: Long,
    // 条目最近一次激活时对应的故事生成步数。
    @ColumnInfo(defaultValue = "NULL")
    val activatedAtStep: Int? = null,
    // 条目保持激活状态的截止生成步数。
    @ColumnInfo(defaultValue = "NULL")
    val stickyUntilStep: Int? = null,
    // 条目冷却状态的截止生成步数。
    @ColumnInfo(defaultValue = "NULL")
    val cooldownUntilStep: Int? = null,
    // 生成时序状态对应的条目配置与宏作用域签名。
    @ColumnInfo(defaultValue = "NULL")
    val stateSignature: String? = null
) {
    /** 清除与旧宏作用域或旧条目签名关联的时序状态。 */
    fun withoutRuntimeState(): StoryLorebookEntry {
        return copy(
            activatedAtStep = null,
            stickyUntilStep = null,
            cooldownUntilStep = null,
            stateSignature = null,
        )
    }
}
