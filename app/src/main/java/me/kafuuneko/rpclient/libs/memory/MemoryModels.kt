package me.kafuuneko.rpclient.libs.memory

import me.kafuuneko.rpclient.libs.room.entity.MemoryFields

/** 编辑目标由类别和正 ID 唯一确定，不按名称或跨表主键推断。 */
sealed interface MemoryTarget {
    val id: Long

    data class Character(override val id: Long) : MemoryTarget
    data class Chat(override val id: Long) : MemoryTarget
    data class Group(override val id: Long) : MemoryTarget
}

/** 物理表身份同时用于本地引用和便携包，不能省略类别。 */
enum class MemoryTable { Character, Chat, Group }

/** 在三张独立主表中定位一个条目。 */
data class MemoryRef(val table: MemoryTable, val id: Long)

/** 保存的事实类别与生效状态。 */
enum class MemoryKind { fact, event, preference, promise, relationship, clue }
enum class MemoryStatus { Active, Disputed, Invalidated }

/** 跨表共用的只读条目，不暴露 DAO 或页面状态。 */
data class MemoryEntry(
    val ref: MemoryRef,
    val target: MemoryTarget,
    val privateCharacterId: Long?,
    val overrideCharacterMemoryId: Long?,
    val fields: MemoryFields,
)

/** 用户提交的内容与范围；普通修改不允许隐式改变条目身份。 */
data class MemoryDraft(
    val body: String,
    val kind: MemoryKind = MemoryKind.fact,
    val status: MemoryStatus = MemoryStatus.Active,
    val importance: Int = 50,
    val pinned: Boolean = false,
    val personaId: Long? = null,
    val privateCharacterId: Long? = null,
    val overrideCharacterMemoryId: Long? = null,
)

/** 条目已被另一编辑器修改，调用方保留草稿并提供刷新入口。 */
class MemoryConflictException : IllegalStateException("Memory revision changed")

/** 目标已被删除，页面应提示后结束，而不是转向其他范围。 */
class MemoryTargetMissingException : IllegalStateException("Memory target no longer exists")
