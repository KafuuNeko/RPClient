package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId")
    ]
)
data class ChatMessage(
    // Message ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 对话 ID
    val sessionId: Long,
    // 创建时间
    val createTime: Long,
    // 来源类型
    val source: Source,
    // 消息内容
    val content: String,
    // 当前Message是否已被总结
    val isSummarized: Boolean,
) {
    enum class Source { Char, User, System }
}
