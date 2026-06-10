package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_chat_messages",
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
        Index(value = ["sessionId", "createTime"]),
        Index("speakerCharacterId"),
        Index("generationBatchId")
    ]
)
data class GroupChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val createTime: Long,
    val source: Source,
    val content: String,
    val speakerCharacterId: Long? = null,
    val speakerNameSnapshot: String,
    val generationBatchId: String? = null
) {
    enum class Source {
        Character,
        User,
        System
    }
}
