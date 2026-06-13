package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 群聊消息实体。
 *
 * [speakerNameSnapshot] 保存消息生成时的名称，避免角色后续改名改变历史归属；
 * [generationBatchId] 用于标识同一轮自动群聊产生的多条回复。
 */
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
    /** 群聊消息来源。 */
    enum class Source {
        Character,
        User,
        System
    }
}
