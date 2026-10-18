package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import me.kafuuneko.rpclient.libs.room.entity.SearchChunk
import me.kafuuneko.rpclient.libs.room.entity.SearchProgress
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchObject

/** 搜索派生表与有界投影查询；SQL 组合及索引一致性由 Repository 管理。 */
@Dao
interface ContentSearchDao {
    /** 读取各源补建检查点和全库变更代次。 */
    @Query("SELECT * FROM search_progress ORDER BY kind")
    suspend fun progress(): List<SearchProgress>

    /** 原子替换单个补建检查点。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(value: SearchProgress)

    /** 只播种尚未被变更触发器登记的源，不能覆盖较新的代次。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun seed(values: List<SearchSource>)

    /** 每次只读取少量待办，避免大批导入占满内存。 */
    @Query("SELECT * FROM search_sources WHERE dirty = 1 AND changedTime <= :beforeTime ORDER BY changedTime, kind, sourceId LIMIT 16")
    suspend fun pending(beforeTime: Long): List<SearchSource>

    /** 用于提交前校验和删除后的存在性判断。 */
    @Query("SELECT * FROM search_sources WHERE kind = :kind AND sourceId = :id")
    suspend fun source(kind: Int, id: Long): SearchSource?

    /** 删除某源派生块，不触及原始正文。 */
    @Query("DELETE FROM search_chunks WHERE kind = :kind AND sourceId = :id")
    suspend fun deleteChunks(kind: Int, id: Long)

    /** 写入有限大小批次，FTS 外部内容触发器同步索引。 */
    @Insert
    suspend fun insertChunks(chunks: List<SearchChunk>)

    /** 仅允许最新源代次完成索引。 */
    @Query("UPDATE search_sources SET indexedRevision = :revision, dirty = 0 WHERE kind = :kind AND sourceId = :id AND revision = :revision")
    suspend fun acknowledge(kind: Int, id: Long, revision: Long): Int

    /**
     * 回收已完成清理且业务源已不存在的墓碑，每批最多 256 行。
     * 只能由单工作器在本批旧快照全部用完、历史播种完成后调用；不能交给独立定时任务。
     * @return 本批实际删除数，仍存在的 Summary 状态不会删除
     */
    @Query("""DELETE FROM search_sources WHERE rowid IN (
        SELECT s.rowid FROM search_sources s WHERE s.deleted = 1 AND s.dirty = 0
        AND NOT EXISTS (SELECT 1 FROM search_chunks c WHERE c.kind = s.kind AND c.sourceId = s.sourceId)
        AND ((s.kind = 1 AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.id = s.sourceId))
          OR (s.kind = 2 AND NOT EXISTS (SELECT 1 FROM group_chat_messages m WHERE m.id = s.sourceId))
          OR (s.kind = 3 AND NOT EXISTS (SELECT 1 FROM story_chapters c WHERE c.id = s.sourceId)))
        ORDER BY s.kind, s.sourceId LIMIT 256)""")
    suspend fun pruneDeletedSources(): Int

    /** 原子失效所有派生块，供编码升级或显式恢复后从业务原文重新投影。 */
    @Query("UPDATE search_sources SET revision = revision + 1, dirty = 1, changedTime = 0")
    suspend fun invalidateSources()

    /** 用于游标失效及就绪判断。 */
    @Query("SELECT EXISTS(SELECT 1 FROM search_sources WHERE dirty = 1)")
    suspend fun hasPending(): Boolean

    /** 索引补建改变结果集合，也必须使分页游标失效。 */
    @Query("UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0")
    suspend fun invalidatePages()

    /** 当前页才读取正文块，始终核对源的最新代次。 */
    @Query("""SELECT c.* FROM search_chunks c JOIN search_sources s ON s.kind = c.kind AND s.sourceId = c.sourceId
        WHERE c.rowid = :id AND s.deleted = 0 AND s.dirty = 0 AND s.revision = c.revision""")
    suspend fun chunk(id: Long): SearchChunk?

    /** 按类型读取有界历史源元信息，不加载正文。 */
    @RawQuery
    suspend fun sources(query: SupportSQLiteQuery): List<SearchSource>

    /** 分段读取超长单源，避免 Android CursorWindow 的单行大小限制。 */
    @RawQuery
    suspend fun text(query: SupportSQLiteQuery): String?

    /** 轻量对象元数据，不包含角色卡扩展字段或正文。 */
    @RawQuery
    suspend fun objects(query: SupportSQLiteQuery): List<SearchObject>

    /** 全文聚合、排序及分页在 SQL 内完成。 */
    @RawQuery
    suspend fun matches(query: SupportSQLiteQuery): List<SearchChunkMatch>

    /** 仅重建可派生的全文结构，用于错误后的显式恢复。 */
    @Query("INSERT INTO search_chunks_fts(search_chunks_fts) VALUES('rebuild')")
    suspend fun rebuildFts()

    /** 只在工作器空闲时合并有限数量的 FTS 块，释放更新与删除产生的旧索引片段。 */
    @Query("INSERT INTO search_chunks_fts(search_chunks_fts) VALUES('merge=64,2')")
    suspend fun mergeFts()
}
