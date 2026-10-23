package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 群聊摘要快照；[coveredMessageId] 是该摘要已覆盖的最后一条消息边界。 */
@Entity(
    tableName = "group_chat_summaries",
    foreignKeys = [
        ForeignKey(
            entity = GroupChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "coveredMessageId"])
    ]
)
data class GroupChatSummary(
    // 群聊摘要快照 ID。
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 所属群聊会话 ID；会话删除时摘要级联删除。
    val sessionId: Long,
    // 摘要创建时间，用于选择和恢复历史摘要。
    val createTime: Long,
    // 摘要正文。
    val content: String,
    // 该摘要已覆盖的最后一条群聊消息 ID。
    val coveredMessageId: Long
)
