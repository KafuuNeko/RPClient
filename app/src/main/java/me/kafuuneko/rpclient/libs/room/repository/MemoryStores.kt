package me.kafuuneko.rpclient.libs.room.repository

import me.kafuuneko.rpclient.libs.memory.MemoryEntry
import me.kafuuneko.rpclient.libs.memory.MemoryRef
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.room.dao.CharacterMemoryDao
import me.kafuuneko.rpclient.libs.room.dao.ChatMemoryDao
import me.kafuuneko.rpclient.libs.room.dao.GroupChatMemoryDao
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemory
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemorySource
import me.kafuuneko.rpclient.libs.room.entity.ChatMemory
import me.kafuuneko.rpclient.libs.room.entity.ChatMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.ChatMemorySource
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemory
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMemorySource
import me.kafuuneko.rpclient.libs.room.entity.MemoryContent
import me.kafuuneko.rpclient.libs.room.entity.MemoryFields
import me.kafuuneko.rpclient.libs.room.entity.MemoryRevisionFields
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields

/** 仓库内部三种类型化 DAO 的窄适配层，不承担范围或事务规则。 */
internal interface MemoryStore {
    /**
     * 按所有者读取主表快照。
     *
     * @param ownerId 角色或会话主键 ID
     * @return 对应主表下的全部记忆条目
     */
    suspend fun list(ownerId: Long): List<MemoryEntry>

    /**
     * 只返回可用于当前角色和人设的有效事实。
     *
     * @param ownerId 角色或会话主键 ID
     * @param personaId 当前解析后的实际人设 ID
     * @param speakerId 当前发言角色主键 ID
     * @param excludedChatId 重生成时排除的单聊消息 ID
     * @param excludedGroupId 重生成时排除的群聊消息 ID
     * @return 具备召回资格的记忆条目列表
     */
    suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ): List<MemoryEntry>

    /**
     * 通过本表主键读取单条快照。
     *
     * @param id 本表记忆主键 ID
     * @return 对应条目，不存在时返回 null
     */
    suspend fun get(id: Long): MemoryEntry?

    /**
     * 查询稳定身份，不跨表匹配。
     *
     * @param uuid 条目全局唯一标识
     * @return 匹配条目，不存在时返回 null
     */
    suspend fun byUuid(uuid: String): MemoryEntry?

    /**
     * 按原修订比较并更新身份键。
     *
     * @param id 本表记忆主键 ID
     * @param revision 期望的当前修订号
     * @param personaId 新适用人设 ID
     * @param privateId 新私有读者角色 ID
     * @param slotKey 新规范槽位键
     * @param now 更新时间戳
     * @return 受影响行数
     */
    suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        privateId: Long?,
        slotKey: String,
        now: Long,
    ): Int

    /**
     * 插入独立主表快照，冲突时中止事务。
     *
     * @param ownerId 角色或会话主键 ID
     * @param privateId 私有读者角色 ID，角色通用或共享时为空
     * @param overrideId 局部覆盖的角色通用记忆 ID
     * @param fields 共享记忆字段
     * @return 新插入记录的主键 ID
     */
    suspend fun insert(
        ownerId: Long,
        privateId: Long?,
        overrideId: Long?,
        fields: MemoryFields,
    ): Long

    /**
     * 按原修订比较并更新正文快照。
     *
     * @param id 本表记忆主键 ID
     * @param revision 期望的当前修订号
     * @param content 新记忆正文及元数据
     * @param now 更新时间戳
     * @return 受影响行数
     */
    suspend fun update(id: Long, revision: Long, content: MemoryContent, now: Long): Int

    /**
     * 按原修订比较后删除条目及子记录。
     *
     * @param id 本表记忆主键 ID
     * @param revision 期望的当前修订号
     * @return 受影响行数
     */
    suspend fun delete(id: Long, revision: Long): Int

    /**
     * 返回从新到旧排列的修订。
     *
     * @param id 本表记忆主键 ID
     * @return 修订投影列表
     */
    suspend fun revisions(id: Long): List<MemoryStoredRevision>

    /**
     * 追加完整修订及其会话边界。
     *
     * @param fields 修订快照字段
     * @param boundary 会话生效消息边界 ID
     * @return 新修订主键 ID
     */
    suspend fun appendRevision(fields: MemoryRevisionFields, boundary: Long): Long

    /**
     * 读取一版修订的全部来源。
     *
     * @param revisionId 修订主键 ID
     * @return 来源证据列表
     */
    suspend fun sources(revisionId: Long): List<MemorySourceFields>

    /**
     * 在修订事务中批量写入来源。
     *
     * @param sources 待写入来源证据列表
     */
    suspend fun appendSources(sources: List<MemorySourceFields>)

    /**
     * 在移除外键前查找所有受影响条目。
     *
     * @param chatIds 受影响单聊消息或摘要 ID 集合
     * @param groupIds 受影响群聊消息 ID 集合
     * @param summaryIds 受影响群聊摘要 ID 集合
     * @return 受影响的去重记忆条目列表
     */
    suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ): List<MemoryEntry>

    /**
     * 撤回证据时保留消息关联，供硬删除查到历史依赖。
     *
     * @param chatIds 受影响单聊消息或摘要 ID 集合
     * @param groupIds 受影响群聊消息 ID 集合
     * @param summaryIds 受影响群聊摘要 ID 集合
     */
    suspend fun invalidateSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    )

    /**
     * 硬删除前移除已经失效的证据。
     *
     * @param chatIds 删除的单聊消息或摘要 ID 集合
     * @param groupIds 删除的群聊消息 ID 集合
     * @param summaryIds 删除的群聊摘要 ID 集合
     */
    suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    )

    /**
     * 清理失去依据的历史正文。
     *
     * @param id 本表记忆主键 ID
     */
    suspend fun pruneUnsupportedRevisions(id: Long)
}

/** 内部修订投影保留生效边界，角色通用记忆边界固定为零。 */
internal data class MemoryStoredRevision(
    val id: Long,
    val fields: MemoryRevisionFields,
    val boundary: Long,
)

/** 将 Character 的静态 SQL 结果转换为共享仓库模型。 */
internal class CharacterMemoryStore(private val mDao: CharacterMemoryDao) : MemoryStore {
    override suspend fun byUuid(uuid: String) = mDao.byUuid(uuid)?.entry()

    override suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        privateId: Long?,
        slotKey: String,
        now: Long,
    ) = mDao.changeScope(id, revision, personaId, slotKey, now)

    override suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ) = mDao.eligible(ownerId, personaId, speakerId, excludedChatId, excludedGroupId)
        .map { it.entry() }

    override suspend fun list(ownerId: Long) = mDao.list(ownerId).map { it.entry() }

    override suspend fun get(id: Long) = mDao.get(id)?.entry()

    override suspend fun insert(
        ownerId: Long,
        privateId: Long?,
        overrideId: Long?,
        fields: MemoryFields,
    ) = mDao.insert(CharacterMemory(characterId = ownerId, fields = fields))

    override suspend fun update(id: Long, revision: Long, content: MemoryContent, now: Long) =
        mDao.update(
            id = id,
            expectedRevision = revision,
            body = content.body,
            kind = content.kind,
            status = content.status,
            importance = content.importance,
            pinned = content.pinned,
            now = now,
        )

    override suspend fun delete(id: Long, revision: Long) = mDao.delete(id, revision)

    override suspend fun revisions(id: Long) = mDao.revisions(id).map { revision ->
        MemoryStoredRevision(id = revision.id, fields = revision.fields, boundary = 0L)
    }

    override suspend fun appendRevision(fields: MemoryRevisionFields, boundary: Long) =
        mDao.insertRevision(CharacterMemoryRevision(fields = fields))

    override suspend fun sources(revisionId: Long) = mDao.sources(revisionId).map { it.fields }

    override suspend fun appendSources(sources: List<MemorySourceFields>) =
        mDao.insertSources(sources.map { CharacterMemorySource(fields = it) })

    override suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.affected(chatIds, groupIds, summaryIds).map { it.entry() }

    override suspend fun invalidateSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.invalidateSources(chatIds, groupIds, summaryIds)

    override suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.clearSources(chatIds, groupIds, summaryIds)

    override suspend fun pruneUnsupportedRevisions(id: Long) = mDao.pruneUnsupportedRevisions(id)

    private fun CharacterMemory.entry() = MemoryEntry(
        ref = MemoryRef(table = MemoryTable.Character, id = id),
        target = MemoryTarget.Character(characterId),
        privateCharacterId = null,
        overrideCharacterMemoryId = null,
        fields = fields,
    )
}

/** 将 Chat 的静态 SQL 结果转换为共享仓库模型。 */
internal class ChatMemoryStore(private val mDao: ChatMemoryDao) : MemoryStore {
    override suspend fun byUuid(uuid: String) = mDao.byUuid(uuid)?.entry()

    override suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        privateId: Long?,
        slotKey: String,
        now: Long,
    ) = mDao.changeScope(id, revision, personaId, privateId, slotKey, now)

    override suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ) = mDao.eligible(ownerId, personaId, speakerId, excludedChatId, excludedGroupId)
        .map { it.entry() }

    override suspend fun list(ownerId: Long) = mDao.list(ownerId).map { it.entry() }

    override suspend fun get(id: Long) = mDao.get(id)?.entry()

    override suspend fun insert(
        ownerId: Long,
        privateId: Long?,
        overrideId: Long?,
        fields: MemoryFields,
    ) = mDao.insert(
        ChatMemory(
            sessionId = ownerId,
            privateCharacterId = privateId,
            overrideCharacterMemoryId = overrideId,
            fields = fields,
        ),
    )

    override suspend fun update(id: Long, revision: Long, content: MemoryContent, now: Long) =
        mDao.update(
            id = id,
            expectedRevision = revision,
            body = content.body,
            kind = content.kind,
            status = content.status,
            importance = content.importance,
            pinned = content.pinned,
            now = now,
        )

    override suspend fun delete(id: Long, revision: Long) = mDao.delete(id, revision)

    override suspend fun revisions(id: Long) = mDao.revisions(id).map { revision ->
        MemoryStoredRevision(
            id = revision.id,
            fields = revision.fields,
            boundary = revision.effectiveAfterMessageId,
        )
    }

    override suspend fun appendRevision(fields: MemoryRevisionFields, boundary: Long) =
        mDao.insertRevision(ChatMemoryRevision(fields = fields, effectiveAfterMessageId = boundary))

    override suspend fun sources(revisionId: Long) = mDao.sources(revisionId).map { it.fields }

    override suspend fun appendSources(sources: List<MemorySourceFields>) =
        mDao.insertSources(sources.map { ChatMemorySource(fields = it) })

    override suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.affected(chatIds, groupIds, summaryIds).map { it.entry() }

    override suspend fun invalidateSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.invalidateSources(chatIds, groupIds, summaryIds)

    override suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.clearSources(chatIds, groupIds, summaryIds)

    override suspend fun pruneUnsupportedRevisions(id: Long) = mDao.pruneUnsupportedRevisions(id)

    private fun ChatMemory.entry() = MemoryEntry(
        ref = MemoryRef(table = MemoryTable.Chat, id = id),
        target = MemoryTarget.Chat(sessionId),
        privateCharacterId = privateCharacterId,
        overrideCharacterMemoryId = overrideCharacterMemoryId,
        fields = fields,
    )
}

/** 将 GroupChat 的静态 SQL 结果转换为共享仓库模型。 */
internal class GroupChatMemoryStore(private val mDao: GroupChatMemoryDao) : MemoryStore {
    override suspend fun byUuid(uuid: String) = mDao.byUuid(uuid)?.entry()

    override suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        privateId: Long?,
        slotKey: String,
        now: Long,
    ) = mDao.changeScope(id, revision, personaId, privateId, slotKey, now)

    override suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ) = mDao.eligible(ownerId, personaId, speakerId, excludedChatId, excludedGroupId)
        .map { it.entry() }

    override suspend fun list(ownerId: Long) = mDao.list(ownerId).map { it.entry() }

    override suspend fun get(id: Long) = mDao.get(id)?.entry()

    override suspend fun insert(
        ownerId: Long,
        privateId: Long?,
        overrideId: Long?,
        fields: MemoryFields,
    ) = mDao.insert(
        GroupChatMemory(
            sessionId = ownerId,
            privateCharacterId = privateId,
            overrideCharacterMemoryId = overrideId,
            fields = fields,
        ),
    )

    override suspend fun update(id: Long, revision: Long, content: MemoryContent, now: Long) =
        mDao.update(
            id = id,
            expectedRevision = revision,
            body = content.body,
            kind = content.kind,
            status = content.status,
            importance = content.importance,
            pinned = content.pinned,
            now = now,
        )

    override suspend fun delete(id: Long, revision: Long) = mDao.delete(id, revision)

    override suspend fun revisions(id: Long) = mDao.revisions(id).map { revision ->
        MemoryStoredRevision(
            id = revision.id,
            fields = revision.fields,
            boundary = revision.effectiveAfterMessageId,
        )
    }

    override suspend fun appendRevision(fields: MemoryRevisionFields, boundary: Long) =
        mDao.insertRevision(
            GroupChatMemoryRevision(
                fields = fields,
                effectiveAfterMessageId = boundary,
            ),
        )

    override suspend fun sources(revisionId: Long) = mDao.sources(revisionId).map { it.fields }

    override suspend fun appendSources(sources: List<MemorySourceFields>) =
        mDao.insertSources(sources.map { GroupChatMemorySource(fields = it) })

    override suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.affected(chatIds, groupIds, summaryIds).map { it.entry() }

    override suspend fun invalidateSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.invalidateSources(chatIds, groupIds, summaryIds)

    override suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ) = mDao.clearSources(chatIds, groupIds, summaryIds)

    override suspend fun pruneUnsupportedRevisions(id: Long) = mDao.pruneUnsupportedRevisions(id)

    private fun GroupChatMemory.entry() = MemoryEntry(
        ref = MemoryRef(table = MemoryTable.Group, id = id),
        target = MemoryTarget.Group(sessionId),
        privateCharacterId = privateCharacterId,
        overrideCharacterMemoryId = overrideCharacterMemoryId,
        fields = fields,
    )
}
