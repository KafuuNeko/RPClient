package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.utils.takeIfNotBlank

/**
 * 单聊会话实体。
 *
 * 会话绑定单个角色卡，并保存用户身份覆盖、世界书选择以及仅供 Prompt 运行时使用的
 * sticky/cooldown 状态。
 */
@Entity(
    tableName = "chat_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Character::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("characterId")
    ]
)
data class ChatSession(
    // 对话 ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 当前对话角色卡
    val characterId: Long,
    // 当前对话创建时间
    val createTime: Long,
    // 当前对话最后打开时间
    val latestTime: Long,
    // 当前对话已启用的世界书条目集
    val lorebookEntrySet: String,
    // 标题
    val title: String,
    // 用户笔记
    val userNote: String,
    // 用户名
    val userName: String = "You",
    // 用户描述
    val userDescription: String = "",
    // 当前对话的角色备注覆盖值，为空时使用关联角色的 creatorNotes
    val creatorNotes: String? = null,
    // 世界书 timed effects 运行时状态，保存 sticky/cooldown 的有效期；不是用户可编辑内容。
    val worldInfoStateJson: String = "{}"
) {
    /** 将空白 creator notes 覆盖归一化为 null，使读取时继续继承角色卡默认值。 */
    fun withNormalizedCreatorNotes(): ChatSession {
        return copy(creatorNotes = creatorNotes.takeIfNotBlank())
    }
}
