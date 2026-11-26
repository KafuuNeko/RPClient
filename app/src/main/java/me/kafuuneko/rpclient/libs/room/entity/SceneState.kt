package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 当前剧情快照；正文格式由 SceneStateCodec 约束，修订使用乐观锁。 */
@Entity(
    tableName = "scene_states",
    foreignKeys = [ForeignKey(
        entity = ConversationToolPolicy::class,
        parentColumns = ["conversationKey"],
        childColumns = ["conversationKey"],
        onDelete = ForeignKey.CASCADE
    )],
)
data class SceneState(
    @PrimaryKey val conversationKey: String,
    val revision: Long = 0,
    val stateJson: String = "{}",
    val updatedAt: Long = 0,
)

/**
 * 可回滚的完整修订快照，边界为已提交消息，0 表示会话起点。
 * - 完整快照让保留窗口的最旧项天然成为基线，无需依赖已清理的差异链。
 */
@Entity(
    tableName = "scene_state_revisions",
    foreignKeys = [ForeignKey(
        entity = SceneState::class,
        parentColumns = ["conversationKey"],
        childColumns = ["conversationKey"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["conversationKey", "revision"], unique = true)],
)
data class SceneStateRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationKey: String,
    val revision: Long,
    val effectiveAfterMessageId: Long,
    val stateJson: String,
    val createdAt: Long,
)
