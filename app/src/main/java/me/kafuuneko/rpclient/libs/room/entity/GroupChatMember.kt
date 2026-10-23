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
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("sessionId"),
        Index("characterId"),
        Index(value = ["sessionId", "sortOrder"])
    ]
)
data class GroupChatMember(
    // 所属群聊会话 ID；会话删除时成员关系级联删除。
    val sessionId: Long,
    // 成员角色 ID；角色删除时成员关系级联删除。
    val characterId: Long,
    // 成员在群聊和 Join 模式角色卡中的顺序，数值越小越靠前。
    val sortOrder: Int,
    // 是否禁止成员自动发言；其角色卡是否进入 Prompt 还受会话 includeMutedCards 控制。
    val muted: Boolean = false
)
