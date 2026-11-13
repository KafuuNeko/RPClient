package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.defaults.DefaultNames

/** 由设置页维护、可被多个会话引用的用户身份。 */
@Entity(
    tableName = "user_personas",
    foreignKeys = [ForeignKey(
        entity = FileEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["avatarFileUuid"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("avatarFileUuid")]
)
data class UserPersona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = DefaultNames.USER,
    val description: String = "",
    val avatarFileUuid: String? = null,
) {
    /**
     * 按会话的两个原始字段分别解析本轮使用的名称和描述。
     * @param nameOverride 名称覆盖，null 或空串表示继承。
     * @param descriptionOverride 描述覆盖，独立于名称判断是否继承。
     * @return 用于本轮展示或生成的身份快照。
     */
    fun withOverrides(nameOverride: String?, descriptionOverride: String?): UserPersona = copy(
        name = nameOverride?.takeIf(String::isNotEmpty) ?: name,
        description = descriptionOverride?.takeIf(String::isNotEmpty) ?: description,
    )

    companion object {
        const val NONE = -1L
        const val INITIAL_ID = 1L
    }
}

/** 默认选择的单行指针与人设变更在同一个数据库事务中提交。 */
@Entity(
    tableName = "user_persona_settings",
    foreignKeys = [ForeignKey(
        entity = UserPersona::class,
        parentColumns = ["id"],
        childColumns = ["defaultPersonaId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("defaultPersonaId")]
)
data class UserPersonaSettings(@PrimaryKey val id: Int = 1, val defaultPersonaId: Long)
