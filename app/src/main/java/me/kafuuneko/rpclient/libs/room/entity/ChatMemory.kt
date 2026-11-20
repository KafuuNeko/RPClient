package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Chat 范围的当前记忆快照；身份键和修订由仓库事务维护。 */
@Entity(
    tableName = "chat_memories",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = UserPersona::class,
        parentColumns = ["id"],
        childColumns = ["personaId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = CharacterMemory::class,
        parentColumns = ["id"],
        childColumns = ["overrideCharacterMemoryId"],
        onDelete = ForeignKey.SET_NULL
    ), ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["privateCharacterId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("personaId"), Index(
        value = ["uuid"],
        unique = true
    ), Index(
        value = ["sessionId", "slotKey", "subjectKey", "factKey"],
        unique = true
    ), Index(value = ["sessionId", "status", "pinned", "importance"]), Index("privateCharacterId"), Index(
        "overrideCharacterMemoryId"
    )],
)
data class ChatMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val privateCharacterId: Long? = null,
    val overrideCharacterMemoryId: Long? = null,
    @Embedded val fields: MemoryFields,
)

/** 条目的不可变修订；删除条目时由外键清理。 */
@Entity(
    tableName = "chat_memory_revisions",
    foreignKeys = [ForeignKey(
        entity = ChatMemory::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["entryId", "revision"], unique = true)],
)
data class ChatMemoryRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val fields: MemoryRevisionFields,
    val effectiveAfterMessageId: Long = 0,
)

/** 修订证据显式引用消息或摘要，不依赖 Agent 表即可存活。 */
@Entity(
    tableName = "chat_memory_sources",
    foreignKeys = [
        ForeignKey(
            entity = ChatMemoryRevision::class,
            parentColumns = ["id"],
            childColumns = ["revisionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["chatMessageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupChatMessage::class,
            parentColumns = ["id"],
            childColumns = ["groupMessageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupChatSummary::class,
            parentColumns = ["id"],
            childColumns = ["groupSummaryId"],
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [Index(
        value = ["revisionId", "sourceKey"],
        unique = true
    ), Index("chatMessageId"), Index("groupMessageId"), Index("groupSummaryId")],
)
data class ChatMemorySource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val fields: MemorySourceFields,
)
