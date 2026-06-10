package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
