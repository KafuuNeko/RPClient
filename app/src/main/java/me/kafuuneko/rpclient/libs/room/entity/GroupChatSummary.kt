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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "coveredMessageId"])
    ]
)
data class GroupChatSummary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val createTime: Long,
    val content: String,
    val coveredMessageId: Long
)
