package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** 三类主表共享的持久化字段；同构字段只定义一次。 */
data class MemoryFields(
    val uuid: String,
    val personaId: Long? = null,
    val subjectKey: String,
    val factKey: String,
    val slotKey: String,
    @Embedded val content: MemoryContent,
    val currentRevision: Long = 1,
    val createdAt: Long,
    val updatedAt: Long,
)

/** 当前正文快照与每版不可变修订共享的内容。 */
data class MemoryContent(
    val body: String,
    val kind: String,
    val status: String,
    @ColumnInfo(defaultValue = "50") val importance: Int = 50,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
)

/** 修订保留当时的正文、生效边界及覆盖目标稳定身份。 */
data class MemoryRevisionFields(
    val entryId: Long,
    val revision: Long,
    @Embedded val content: MemoryContent,
    val overrideCharacterMemoryUuid: String? = null,
    val snapshotPersonaId: Long? = null,
    val snapshotPrivateCharacterId: Long? = null,
    val origin: String,
    val reason: String,
    val createdAt: Long,
)

/** 每一版保存其全部证据，正文和来源摘录只留在本地资料层。 */
data class MemorySourceFields(
    val revisionId: Long,
    val sourceKind: String,
    val confidence: String,
    val valid: Boolean = true,
    val sourceKey: String,
    val chatMessageId: Long? = null,
    val groupMessageId: Long? = null,
    val groupSummaryId: Long? = null,
    val authorKey: String = "",
    val fingerprint: String = "",
    val quote: String = "",
    val rangeStart: Int? = null,
    val rangeEnd: Int? = null,
    val originRef: String = "",
    val createdAt: Long,
)
