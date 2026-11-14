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

    /**
     * 单聊和群聊可显式选中「跟随默认人设」，预览仍使用真实姓名。
     * @param id 会话绑定 ID，省略时预选当前默认人设。
     * @return 候选项、选中 ID 和默认人设的显示名称。
     */
    suspend fun chatSelection(id: Long? = null) = mDatabase.withTransaction {
        val active = mDao.active()
        PersonaSelection(mDao.list(), id ?: active.id, followDefaultName = active.name)
    }

    /**
     * 读取聊天实际继承的姓名、描述和头像。
     * @param id 会话绑定 ID，跟随默认项时读取当前默认指针。
     * @return 未应用会话覆盖的完整人设。
     */
    suspend fun chatPersona(id: Long): UserPersona =
        if (id == UserPersona.FOLLOW_DEFAULT) active() else mDao.get(id)

    /**
     * 返回创建会话时的默认项。
     * @return 当前默认指针指向的完整人设。
     */
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
    suspend fun resolve(session: ChatSession) =
        chatPersona(session.userPersonaId).withOverrides(session.userName, session.userDescription)

    /**
     * 解析当前群聊的有效身份。
     * @param session 带有绑定 ID 和两个独立覆盖字段的群聊。
     * @return 名称与描述逐字段合并后的身份快照。
     */
    suspend fun resolve(session: GroupChatSession) =
        chatPersona(session.userPersonaId).withOverrides(session.userName, session.userDescription)

    /**
     * 在同一读取事务中冻结单聊绑定、默认指针及字段覆盖，供一次发送复用。
     * @param sessionId 目标单聊 ID。
     * @return 当前有效身份；会话已删除时返回 null。
     */
    suspend fun resolveChat(sessionId: Long): UserPersona? = mDatabase.withTransaction {
        mDatabase.getChatSessionDao().getSessionById(sessionId)?.let { resolve(it) }
    }

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
     * 删除默认项时启用最早创建的剩余人设；删除最后一项时新建初始人设，随解绑一并提交。
     * @param id 待删除的人设 ID。
     * @return 是否删除了指定身份。
     */
    suspend fun delete(id: Long): Boolean = mFiles.mutate {
        // 先确定替代默认项，保证解绑和删除期间默认指针始终有效。
        val persona = mDao.find(id) ?: return@mutate false
        val wasDefault = mDao.active().id == id
        if (wasDefault) {
            val replacement =
                mDao.list().firstOrNull { it.id != id }?.id ?: mDao.insert(UserPersona())
            select(replacement)
        }

        // 会话保留原发言者姓名，故事切换为不注入；最后交还独立的头像 UUID。
        mDao.detachChats(id, persona.name, wasDefault)
        mDao.detachGroups(id, persona.name, wasDefault)
        mDao.detachStories(id)
        mDao.delete(id)
        persona.avatarFileUuid?.let { removeFiles(listOf(it)) }
        true
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
        val persona = mDao.find(id)
        if (persona == null) {
            uuid?.let { removeFiles(listOf(it)) }
            return@mutate
        }
        val old = persona.avatarFileUuid
        mDao.setAvatar(id, uuid)
        if (old != null && old != uuid) removeFiles(listOf(old))
    }
}
