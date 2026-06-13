package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 群聊与角色的多对多关系。
 *
 * 复合主键防止同一角色重复加入；[sortOrder] 决定成员和 Join 模式角色卡顺序，
 * [muted] 只禁止自动发言，不删除成员。
 */
@Entity(
    tableName = "group_chat_members",
    primaryKeys = ["sessionId", "characterId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupChatSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index("characterId"),
        Index(value = ["sessionId", "sortOrder"])
    ]
)
data class GroupChatMember(
    val sessionId: Long,
    val characterId: Long,
    val sortOrder: Int,
    val muted: Boolean = false
)
