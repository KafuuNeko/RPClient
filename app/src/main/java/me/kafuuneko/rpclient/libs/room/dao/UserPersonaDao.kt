package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.model.PersonaOption

/** 用户身份库的查询、创建和按字段保存入口。 */
@Dao
interface UserPersonaDao {
    /** 按创建顺序列出选择项。 */
    @Query("SELECT id, name FROM user_personas ORDER BY id")
    suspend fun list(): List<PersonaOption>

    /**
     * 读取会话绑定的人设。
     * @param id 已持久化的正 ID，禁用项由 Repository 处理。
     * @return 指定身份的完整记录。
     */
    @Query("SELECT * FROM user_personas WHERE id = :id")
    suspend fun get(id: Long): UserPersona

    /** 分别应用当前会话的名称、描述覆盖。 */
    suspend fun resolve(session: ChatSession) =
        get(session.userPersonaId).withOverrides(session.userName, session.userDescription)

    /** 群聊也沿用逐字段的继承规则。 */
    suspend fun resolve(session: GroupChatSession) =
        get(session.userPersonaId).withOverrides(session.userName, session.userDescription)

    /** 读取默认指针与其人设。 */
    @Query("SELECT p.* FROM user_personas p JOIN user_persona_settings s ON s.defaultPersonaId = p.id WHERE s.id = 1")
    suspend fun active(): UserPersona

    /** 保存新的独立人设。 */
    @Insert
    suspend fun insert(persona: UserPersona): Long

    /**
     * 显式切换默认项。
     * @param id 目标人设 ID。
     * @return 更新的默认指针行数，正常初始化后应为 1。
     */
    @Query("UPDATE user_persona_settings SET defaultPersonaId = :id WHERE id = 1")
    suspend fun select(id: Long): Int

    /**
     * 仅修改指定人设的名称。
     * @param id 被编辑的身份 ID。
     * @param name 持久化后的显示名称。
     */
    @Query("UPDATE user_personas SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    /**
     * 仅修改指定人设的描述。
     * @param id 被编辑的身份 ID。
     * @param description 保留空白和宏的描述原文。
     */
    @Query("UPDATE user_personas SET description = :description WHERE id = :id")
    suspend fun describe(id: Long, description: String)

    /**
     * 替换文件引用，由调用方在同一事务中处理旧文件释放。
     * @param id 被编辑的身份 ID。
     * @param uuid 本头像的独立文件索引，null 表示清空。
     */
    @Query("UPDATE user_personas SET avatarFileUuid = :uuid WHERE id = :id")
    suspend fun setAvatar(id: Long, uuid: String?)
}
