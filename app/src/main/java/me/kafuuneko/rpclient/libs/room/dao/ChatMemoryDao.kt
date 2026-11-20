package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.entity.ChatMemory
import me.kafuuneko.rpclient.libs.room.entity.ChatMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.ChatMemorySource

/**
 * Chat 记忆的静态 SQL 访问入口，业务事务统一由 MemoryRepository 编排。
 * - 不继承 MutableDao，父表禁止 REPLACE 及无版本号覆盖，避免级联删除修订与证据。
 */
@Dao
interface ChatMemoryDao {
    /**
     * 读取目标单聊会话的全部记忆条目，后续由仓库按实际读者及人设过滤。
     *
     * @param ownerId 单聊会话主键 ID
     * @return 按主键升序排列的单聊记忆列表
     */
    @Query("SELECT * FROM chat_memories WHERE sessionId = :ownerId ORDER BY id")
    suspend fun list(ownerId: Long): List<ChatMemory>

    /**
     * 按本表主键读取单条记忆，不与其他表的相同 ID 混用。
     *
     * @param id 单聊记忆主键 ID
     * @return 对应记录，不存在时返回 null
     */
    @Query("SELECT * FROM chat_memories WHERE id = :id")
    suspend fun get(id: Long): ChatMemory?

    /**
     * 便携包只按稳定 UUID 查找，名称及外部主键不参与合并。
     *
     * @param uuid 条目全局唯一标识
     * @return 匹配记录，不存在时返回 null
     */
    @Query("SELECT * FROM chat_memories WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): ChatMemory?

    /**
     * 范围变更保持条目身份，更新冲突必须由调用方回滚。
     *
     * @param id 单聊记忆主键 ID
     * @param revision 期望的当前修订号
     * @param personaId 新的适用人设 ID，null 表示不限人设
     * @param privateId 新的私有读者角色 ID，null 表示会话共享
     * @param slotKey 新的规范槽位键
     * @param now 更新时间戳
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("UPDATE chat_memories SET personaId = :personaId, privateCharacterId = :privateId, slotKey = :slotKey, currentRevision = currentRevision + 1, updatedAt = :now WHERE id = :id AND currentRevision = :revision")
    suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        privateId: Long?,
        slotKey: String,
        now: Long,
    ): Int

    /**
     * 通用记忆缩小人设范围时解除不再兼容的会话覆盖，保留局部条目。
     *
     * @param id 被修改范围的角色通用记忆 ID
     * @param personaId 通用记忆收窄后的目标人设 ID
     */
    @Query("UPDATE chat_memories SET overrideCharacterMemoryId = NULL WHERE overrideCharacterMemoryId = :id AND (personaId IS NULL OR personaId != :personaId)")
    suspend fun detachIncompatibleOverrides(id: Long, personaId: Long)

    /**
     * 首次写入使用 ABORT，避免覆盖已有身份及子表。
     *
     * @param entry 待插入的单聊记忆实体
     * @return 新插入记录的主键 ID
     */
    @Insert
    suspend fun insert(entry: ChatMemory): Long

    /**
     * 比较已读取版本后更新快照，返回零代表并发冲突。
     *
     * @param id 单聊记忆主键 ID
     * @param expectedRevision 期望的当前修订号
     * @param body 新记忆正文
     * @param kind 记忆类别名称
     * @param status 记忆状态名称
     * @param importance 重要度分值
     * @param pinned 是否置顶
     * @param now 更新时间戳
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("UPDATE chat_memories SET body = :body, kind = :kind, status = :status, importance = :importance, pinned = :pinned, currentRevision = currentRevision + 1, updatedAt = :now WHERE id = :id AND currentRevision = :expectedRevision")
    suspend fun update(
        id: Long,
        expectedRevision: Long,
        body: String,
        kind: String,
        status: String,
        importance: Int,
        pinned: Boolean,
        now: Long,
    ): Int

    /**
     * 删除前同样比较版本，不能删除另一编辑器刚保存的记录。
     *
     * @param id 单聊记忆主键 ID
     * @param expectedRevision 期望的当前修订号
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("DELETE FROM chat_memories WHERE id = :id AND currentRevision = :expectedRevision")
    suspend fun delete(id: Long, expectedRevision: Long): Int

    /**
     * 按版本倒序读取修订，为证据展示和分支快照提供依据。
     *
     * @param id 单聊记忆主键 ID
     * @return 按修订号降序排列的修订列表
     */
    @Query("SELECT * FROM chat_memory_revisions WHERE entryId = :id ORDER BY revision DESC")
    suspend fun revisions(id: Long): List<ChatMemoryRevision>

    /**
     * 追加不可变修订，重复版本直接失败。
     *
     * @param revision 待追加的修订记录
     * @return 新修订主键 ID
     */
    @Insert
    suspend fun insertRevision(revision: ChatMemoryRevision): Long

    /**
     * 读取指定修订的全部证据。
     *
     * @param revisionId 修订记录主键 ID
     * @return 按主键升序排列的来源证据列表
     */
    @Query("SELECT * FROM chat_memory_sources WHERE revisionId = :revisionId ORDER BY id")
    suspend fun sources(revisionId: Long): List<ChatMemorySource>

    /**
     * 与主表快照、修订共享事务追加证据。
     *
     * @param sources 待批量写入的来源证据列表
     */
    @Insert
    suspend fun insertSources(sources: List<ChatMemorySource>)

    /**
     * 在来源变更前收集跨范围受影响条目。
     *
     * @param chatIds 变更的单聊消息或单聊摘要 ID 集合
     * @param groupIds 变更的群聊消息 ID 集合
     * @param summaryIds 变更的群聊摘要 ID 集合
     * @return 引用了上述任意来源的去重记忆条目列表
     */
    @Query("SELECT DISTINCT m.* FROM chat_memories m JOIN chat_memory_revisions r ON r.entryId = m.id JOIN chat_memory_sources s ON s.revisionId = r.id WHERE s.chatMessageId IN (:chatIds) OR s.groupMessageId IN (:groupIds) OR s.groupSummaryId IN (:summaryIds)")
    suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ): List<ChatMemory>

    /**
     * 消息编辑先清空摘录，保留外键以便之后硬删除清理派生正文。
     *
     * @param chatIds 变更的单聊消息或单聊摘要 ID 集合
     * @param groupIds 变更的群聊消息 ID 集合
     * @param summaryIds 变更的群聊摘要 ID 集合
     */
    @Query(
        """
        UPDATE chat_memory_sources
        SET valid = 0, quote = '', fingerprint = '', rangeStart = NULL, rangeEnd = NULL
        WHERE chatMessageId IN (:chatIds) OR groupMessageId IN (:groupIds) OR groupSummaryId IN (:summaryIds)
    """,
    )
    suspend fun invalidateSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    )

    /**
     * 硬删除时直接移除来源关系及其快照。
     *
     * @param chatIds 删除的单聊消息或单聊摘要 ID 集合
     * @param groupIds 删除的群聊消息 ID 集合
     * @param summaryIds 删除的群聊摘要 ID 集合
     */
    @Query("DELETE FROM chat_memory_sources WHERE chatMessageId IN (:chatIds) OR groupMessageId IN (:groupIds) OR groupSummaryId IN (:summaryIds)")
    suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    )

    /**
     * 隐私清理优先于历史保留，移除已无任何支持的旧修订正文。
     *
     * @param id 单聊记忆主键 ID
     */
    @Query("DELETE FROM chat_memory_revisions WHERE entryId = :id AND NOT EXISTS(SELECT 1 FROM chat_memory_sources s WHERE s.revisionId = chat_memory_revisions.id AND s.valid = 1)")
    suspend fun pruneUnsupportedRevisions(id: Long)

    /**
     * SQL 先限制角色、人设、生效状态和当前版有效证据，排除重生成的唯一来源。
     *
     * @param ownerId 单聊会话主键 ID
     * @param personaId 当前会话解析后的实际人设 ID
     * @param speakerId 当前发言角色主键 ID
     * @param excludedChatId 重生成时排除的单聊消息 ID
     * @param excludedGroupId 重生成时排除的群聊消息 ID
     * @return 具备有效资格的单聊记忆列表
     */
    @Query("SELECT m.* FROM chat_memories m WHERE m.sessionId = :ownerId AND (m.personaId IS NULL OR m.personaId = :personaId) AND m.status = 'Active' AND (m.privateCharacterId IS NULL OR m.privateCharacterId = :speakerId) AND EXISTS(SELECT 1 FROM chat_memory_revisions r JOIN chat_memory_sources s ON s.revisionId = r.id WHERE r.entryId = m.id AND r.revision = m.currentRevision AND s.valid = 1 AND (s.chatMessageId IS NULL OR s.chatMessageId != :excludedChatId) AND (s.groupMessageId IS NULL OR s.groupMessageId != :excludedGroupId))")
    suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ): List<ChatMemory>
}
