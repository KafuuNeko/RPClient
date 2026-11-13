package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.UserPersona
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection

/**
 * 管理可被多个会话绑定的用户身份。
 * - 默认项的切换与创建共同提交。
 * - 故事禁用人设时只返回中性宏上下文。
 */
class UserPersonaRepository(
    private val mDatabase: AppDatabase,
    private val mFiles: FileRepository
) {
    private val mDao = mDatabase.getUserPersonaDao()

    /**
     * 返回人设选择快照。
     * @param id 当前会话或草稿的绑定 ID，省略时采用全局默认项。
     * @return 名称候选项和独立的选中 ID。
     */
    suspend fun selection(id: Long? = null) = mDatabase.withTransaction {
        PersonaSelection(mDao.list(), id ?: mDao.active().id)
    }

    /** 返回创建会话时的默认项。 */
    suspend fun active() = mDao.active()

    /**
     * 获取指定人设或故事专用的中性上下文。
     * @param id 人设正 ID，故事的禁用值为 [UserPersona.NONE]。
     * @return 禁用时返回不含头像和描述的中性身份。
     */
    suspend fun get(id: Long) = if (id == UserPersona.NONE) UserPersona(id = id) else mDao.get(id)

    /**
     * 解析当前单聊的有效身份。
     * @param session 带有绑定 ID 和两个独立覆盖字段的会话。
     * @return 名称与描述逐字段合并后的身份快照。
     */
    suspend fun resolve(session: ChatSession) = mDao.resolve(session)

    /**
     * 解析当前群聊的有效身份。
     * @param session 带有绑定 ID 和两个独立覆盖字段的群聊。
     * @return 名称与描述逐字段合并后的身份快照。
     */
    suspend fun resolve(session: GroupChatSession) = mDao.resolve(session)

    /**
     * 在同一事务内创建并启用新人设。
     * @param name 用户输入的非空白名称。
     * @return 新建的人设 ID。
     */
    suspend fun create(name: String) = mDatabase.withTransaction {
        require(name.isNotBlank())
        val id = mDao.insert(UserPersona(name = name.trim()))
        select(id)
        id
    }

    /**
     * 选择已有的默认人设。
     * @param id 目标人设 ID，仅更新全局默认指针。
     */
    suspend fun select(id: Long) {
        check(mDao.select(id) == 1)
    }

    /**
     * 保存名称，保留已有描述与头像。
     * @param id 被编辑的人设 ID。
     * @param name 已按输入规则处理的名称。
     */
    suspend fun rename(id: Long, name: String) = mDao.rename(id, name)

    /**
     * 保存描述原文。
     * @param id 被编辑的人设 ID。
     * @param value 含空白、换行及宏的原始描述。
     */
    suspend fun describe(id: Long, value: String) = mDao.describe(id, value)

    /**
     * 在同一文件事务里替换头像并释放旧引用。
     * @param id 被编辑的人设 ID。
     * @param uuid 文件仓库为本头像签发的独立 UUID，null 表示清空。
     */
    suspend fun setAvatar(id: Long, uuid: String?) = mFiles.mutate {
        val old = mDao.get(id).avatarFileUuid
        mDao.setAvatar(id, uuid)
        if (old != null && old != uuid) removeFiles(listOf(old))
    }
}
