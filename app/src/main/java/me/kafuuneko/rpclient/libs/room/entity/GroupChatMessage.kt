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
        Index(value = ["sessionId", "createTime", "id"]),
        Index("speakerCharacterId"),
        Index("generationBatchId")
    ]
)
data class GroupChatMessage(
    // 群聊消息 ID。
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 所属群聊会话 ID；会话删除时消息级联删除。
    val sessionId: Long,
    // 消息创建时间。
    val createTime: Long,
    // 消息来源类型，用于区分角色、用户和系统消息。
    val source: Source,
    // 消息正文。
    val content: String,
    // 发言角色 ID；用户或系统消息为空，角色删除后历史消息仍保留该快照关联值。
    val speakerCharacterId: Long? = null,
    // 消息生成时的发言者名称快照，避免角色改名影响历史显示。
    val speakerNameSnapshot: String,
    // 自动群聊同一生成轮次的批次 ID；普通或手动消息为空。
    val generationBatchId: String? = null
) {
    /** 群聊消息来源。 */
    enum class Source {
        Character,
        User,
        System
    }
}
