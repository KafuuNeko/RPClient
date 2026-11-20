package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Character 范围的当前记忆快照；身份键和修订由仓库事务维护。 */
@Entity(
    tableName = "character_memories",
    foreignKeys = [ForeignKey(
        entity = Character::class,
        parentColumns = ["id"],
        childColumns = ["characterId"],
        onDelete = ForeignKey.CASCADE
    ), ForeignKey(
        entity = UserPersona::class,
        parentColumns = ["id"],
        childColumns = ["personaId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("characterId"), Index("personaId"), Index(
        value = ["uuid"],
        unique = true
    ), Index(value = ["characterId", "slotKey", "subjectKey", "factKey"], unique = true), Index(
        value = ["characterId", "status", "pinned", "importance"]
    )],
)
data class CharacterMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: Long,
    @Embedded val fields: MemoryFields,
)

/** 条目的不可变修订；删除条目时由外键清理。 */
@Entity(
    tableName = "character_memory_revisions",
    foreignKeys = [ForeignKey(
        entity = CharacterMemory::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["entryId", "revision"], unique = true)],
)
data class CharacterMemoryRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val fields: MemoryRevisionFields,
)

/** 修订证据显式引用消息或摘要，不依赖 Agent 表即可存活。 */
@Entity(
    tableName = "character_memory_sources",
    foreignKeys = [
        ForeignKey(
            entity = CharacterMemoryRevision::class,
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
data class CharacterMemorySource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @Embedded val fields: MemorySourceFields,
)
