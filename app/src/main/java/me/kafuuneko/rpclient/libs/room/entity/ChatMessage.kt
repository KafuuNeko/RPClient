package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 单聊消息实体。
 *
 * Summary 与普通消息共用一张表；Summary 通过 [coveredMessageId] 记录覆盖边界，
 * 普通消息的该字段始终为 null。
 */
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
        Index("sessionId"),
        Index(value = ["sessionId", "source"])
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
    // Summary 消息覆盖到的最后一条普通消息 id；普通消息固定为 null，0 表示空总结不覆盖消息。
    val coveredMessageId: Long? = null,
) {
    /** 数据层消息来源；Summary 不会直接作为普通聊天历史返回。 */
    enum class Source { Char, User, System, Summary }
}
