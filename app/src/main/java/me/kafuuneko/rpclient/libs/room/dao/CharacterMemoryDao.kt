package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemory
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemoryRevision
import me.kafuuneko.rpclient.libs.room.entity.CharacterMemorySource
import me.kafuuneko.rpclient.libs.room.model.MemoryCharacterOverview

/**
 * Character 记忆的静态 SQL 访问入口，业务事务统一由 MemoryRepository 编排。
 * - 不继承 MutableDao，父表禁止 REPLACE 及无版本号覆盖，避免级联删除修订与证据。
 */
@Dao
interface CharacterMemoryDao {
    /**
     * 读取目标角色的通用记忆条目，后续由仓库按实际读者及人设过滤。
     *
     * @param ownerId 角色主键 ID
     * @return 按主键升序排列的角色通用记忆列表
     */
    @Query("SELECT * FROM character_memories WHERE characterId = :ownerId ORDER BY id")
    suspend fun list(ownerId: Long): List<CharacterMemory>

    /**
     * 按本表主键读取单条记忆，不与其他表的相同 ID 混用。
     *
     * @param id 角色记忆主键 ID
     * @return 对应记录，不存在时返回 null
     */
    @Query("SELECT * FROM character_memories WHERE id = :id")
    suspend fun get(id: Long): CharacterMemory?

    /**
     * 按稳定 UUID 查找条目，便携包合并时名称及外部主键不参与匹配。
     *
     * @param uuid 条目全局唯一标识
     * @return 匹配记录，不存在时返回 null
     */
    @Query("SELECT * FROM character_memories WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): CharacterMemory?

    /**
     * 范围变更保持条目身份，更新冲突必须由调用方回滚。
     *
     * @param id 角色记忆主键 ID
     * @param revision 期望的当前修订号
     * @param personaId 新的适用人设 ID，null 表示不限人设
     * @param slotKey 新的规范槽位键
     * @param now 更新时间戳
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("UPDATE character_memories SET personaId = :personaId, slotKey = :slotKey, currentRevision = currentRevision + 1, updatedAt = :now WHERE id = :id AND currentRevision = :revision")
    suspend fun changeScope(
        id: Long,
        revision: Long,
        personaId: Long?,
        slotKey: String,
        now: Long,
    ): Int

    /**
     * 首次写入使用 ABORT，避免覆盖已有身份及子表。
     *
     * @param entry 待插入的角色记忆实体
     * @return 新插入记录的主键 ID
     */
    @Insert
    suspend fun insert(entry: CharacterMemory): Long

    /**
     * 比较已读取版本后更新快照，返回零代表并发冲突。
     *
     * @param id 角色记忆主键 ID
     * @param expectedRevision 期望的当前修订号
     * @param body 新记忆正文
     * @param kind 记忆类别名称
     * @param status 记忆状态名称
     * @param importance 重要度分值
     * @param pinned 是否置顶
     * @param now 更新时间戳
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("UPDATE character_memories SET body = :body, kind = :kind, status = :status, importance = :importance, pinned = :pinned, currentRevision = currentRevision + 1, updatedAt = :now WHERE id = :id AND currentRevision = :expectedRevision")
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
     * @param id 角色记忆主键 ID
     * @param expectedRevision 期望的当前修订号
     * @return 受影响行数，0 表示发生并发版本冲突
     */
    @Query("DELETE FROM character_memories WHERE id = :id AND currentRevision = :expectedRevision")
    suspend fun delete(id: Long, expectedRevision: Long): Int

    /**
     * 按版本倒序读取修订，为证据展示和分支快照提供依据。
     *
     * @param id 角色记忆主键 ID
     * @return 按修订号降序排列的修订列表
     */
    @Query("SELECT * FROM character_memory_revisions WHERE entryId = :id ORDER BY revision DESC")
    suspend fun revisions(id: Long): List<CharacterMemoryRevision>

    /**
     * 追加不可变修订，重复版本直接失败。
     *
     * @param revision 待追加的修订记录
     * @return 新修订主键 ID
     */
    @Insert
    suspend fun insertRevision(revision: CharacterMemoryRevision): Long

    /**
     * 读取指定修订的全部证据。
     *
     * @param revisionId 修订记录主键 ID
     * @return 按主键升序排列的来源证据列表
     */
    @Query("SELECT * FROM character_memory_sources WHERE revisionId = :revisionId ORDER BY id")
    suspend fun sources(revisionId: Long): List<CharacterMemorySource>

    /**
     * 与主表快照、修订共享事务追加证据。
     *
     * @param sources 待批量写入的来源证据列表
     */
    @Insert
    suspend fun insertSources(sources: List<CharacterMemorySource>)

    /**
     * 离群前将该角色引用本群消息或摘要的证据冻结为独立快照，保留全部历史修订。
     *
     * @param sessionId 即将退出的群聊会话 ID
     * @param characterId 即将移出的角色 ID
     */
    @Query(
        """
        UPDATE character_memory_sources
        SET originRef = 'Group/' || :sessionId || '/' || sourceKind || '/' ||
                COALESCE(groupMessageId, groupSummaryId) || '/' || originRef,
            sourceKind = 'PromotedSnapshot', groupMessageId = NULL, groupSummaryId = NULL
        WHERE revisionId IN (
            SELECT r.id FROM character_memory_revisions r
            JOIN character_memories m ON m.id = r.entryId
            WHERE m.characterId = :characterId
        ) AND (
            (sourceKind = 'GroupMessage' AND groupMessageId IN (
                SELECT id FROM group_chat_messages WHERE sessionId = :sessionId
            )) OR (sourceKind = 'GroupSummary' AND groupSummaryId IN (
                SELECT id FROM group_chat_summaries WHERE sessionId = :sessionId
            ))
        )
        """,
    )
    suspend fun snapshotGroupSources(sessionId: Long, characterId: Long)

    /**
     * 左连接保留零条角色，修订与证据数量不影响条目计数。
     *
     * @return 包含全部角色及其通用记忆条数的实时流
     */
    @Query("SELECT c.id, c.name, c.avatar, COUNT(m.id) AS memoryCount FROM character c LEFT JOIN character_memories m ON m.characterId = c.id GROUP BY c.id, c.name, c.avatar ORDER BY c.id DESC")
    fun observeCharacters(): Flow<List<MemoryCharacterOverview>>

    /**
     * 在来源变更前收集跨范围受影响条目。
     *
     * @param chatIds 变更的单聊消息或单聊摘要 ID 集合
     * @param groupIds 变更的群聊消息 ID 集合
     * @param summaryIds 变更的群聊摘要 ID 集合
     * @return 引用了上述任意来源的去重记忆条目列表
     */
    @Query("SELECT DISTINCT m.* FROM character_memories m JOIN character_memory_revisions r ON r.entryId = m.id JOIN character_memory_sources s ON s.revisionId = r.id WHERE s.chatMessageId IN (:chatIds) OR s.groupMessageId IN (:groupIds) OR s.groupSummaryId IN (:summaryIds)")
    suspend fun affected(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    ): List<CharacterMemory>

    /**
     * 消息编辑先清空摘录，保留外键以便之后硬删除清理派生正文。
     *
     * @param chatIds 变更的单聊消息或单聊摘要 ID 集合
     * @param groupIds 变更的群聊消息 ID 集合
     * @param summaryIds 变更的群聊摘要 ID 集合
     */
    @Query(
        """
        UPDATE character_memory_sources
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
    @Query("DELETE FROM character_memory_sources WHERE chatMessageId IN (:chatIds) OR groupMessageId IN (:groupIds) OR groupSummaryId IN (:summaryIds)")
    suspend fun clearSources(
        chatIds: List<Long>,
        groupIds: List<Long>,
        summaryIds: List<Long>,
    )

    /**
     * 隐私清理优先于历史保留，移除已无任何支持的旧修订正文。
     *
     * @param id 角色记忆主键 ID
     */
    @Query("DELETE FROM character_memory_revisions WHERE entryId = :id AND NOT EXISTS(SELECT 1 FROM character_memory_sources s WHERE s.revisionId = character_memory_revisions.id AND s.valid = 1)")
    suspend fun pruneUnsupportedRevisions(id: Long)

    /**
     * SQL 先限制角色、人设、生效状态和当前版有效证据，排除重生成的唯一来源。
     *
     * @param ownerId 角色主键 ID
     * @param personaId 当前会话解析后的实际人设 ID
     * @param speakerId 当前发言角色主键 ID
     * @param excludedChatId 重生成时排除的单聊消息 ID
     * @param excludedGroupId 重生成时排除的群聊消息 ID
     * @return 具备召回资格的角色通用记忆列表
     */
    @Query("SELECT m.* FROM character_memories m WHERE m.characterId = :ownerId AND (m.personaId IS NULL OR m.personaId = :personaId) AND m.status = 'Active' AND m.characterId = :speakerId AND EXISTS(SELECT 1 FROM character_memory_revisions r JOIN character_memory_sources s ON s.revisionId = r.id WHERE r.entryId = m.id AND r.revision = m.currentRevision AND s.valid = 1 AND (s.chatMessageId IS NULL OR s.chatMessageId != :excludedChatId) AND (s.groupMessageId IS NULL OR s.groupMessageId != :excludedGroupId))")
    suspend fun eligible(
        ownerId: Long,
        personaId: Long,
        speakerId: Long,
        excludedChatId: Long,
        excludedGroupId: Long,
    ): List<CharacterMemory>
}
