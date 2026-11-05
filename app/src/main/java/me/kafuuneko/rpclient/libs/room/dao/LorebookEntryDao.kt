package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.model.LorebookEntryCount

/** 世界书条目的基础访问接口，不负责触发扫描、预算裁剪或导入兼容转换。 */
@Dao
interface LorebookEntryDao : MutableDao<LorebookEntry> {
    /**
     * 根据世界书 id 查询该世界书下的所有条目。
     *
     * @param lorebookId 世界书 id。
     * @return 按插入顺序和 id 排列的世界书条目列表。
     */
    @Query("SELECT * FROM lorebook_entries WHERE lorebookId = :lorebookId ORDER BY `order` ASC, id ASC")
    suspend fun getEntriesByLorebookId(lorebookId: Long): List<LorebookEntry>

    /** 一次查询所有世界书条目，供世界书列表和选择器批量组装数据。 */
    @Query(
        "SELECT * FROM lorebook_entries " +
            "ORDER BY lorebookId ASC, `order` ASC, id ASC"
    )
    suspend fun getAllEntries(): List<LorebookEntry>

    /** 一次聚合所有世界书的条目数量，避免列表页对每本世界书发起独立查询。 */
    @Query(
        "SELECT lorebookId, COUNT(*) AS count FROM lorebook_entries " +
            "GROUP BY lorebookId"
    )
    suspend fun getEntryCounts(): List<LorebookEntryCount>

    /** 根据多个条目 ID 批量读取世界书条目，保持关联查询为固定次数。 */
    @Query(
        "SELECT * FROM lorebook_entries WHERE id IN (:ids) " +
            "ORDER BY `order` ASC, id ASC"
    )
    suspend fun getEntriesByIds(ids: List<Long>): List<LorebookEntry>

    /**
     * 根据条目 id 查询世界书条目。
     *
     * @param id 世界书条目 id。
     * @return 匹配的世界书条目；如果不存在则返回 null。
     */
    @Query("SELECT * FROM lorebook_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): LorebookEntry?

    /**
     * 修改世界书条目的正文内容。
     *
     * @param id 世界书条目 id。
     * @param content 新的条目正文内容。
     */
    @Query("UPDATE lorebook_entries SET content = :content WHERE id = :id")
    suspend fun updateEntryContent(id: Long, content: String)

    /**
     * 只修改条目的全局禁用状态，避免读出整行后回写时覆盖并发更新的其他字段。
     *
     * @return 实际更新的行数。
     */
    @Query("UPDATE lorebook_entries SET disabled = :disabled WHERE id = :id")
    suspend fun updateEntryDisabled(id: Long, disabled: Boolean): Int

    /**
     * 根据条目 id 删除世界书条目。
     *
     * @param id 世界书条目 id。
     */
    @Query("DELETE FROM lorebook_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    /** 限定世界书归属后批量删除条目，避免跨书误删。 */
    @Query("DELETE FROM lorebook_entries WHERE lorebookId = :lorebookId AND id IN (:ids)")
    suspend fun deleteEntriesByIds(lorebookId: Long, ids: List<Long>)

    /**
     * 清空指定世界书下的所有条目。
     *
     * 注意：该方法只删除条目，不删除世界书本体。
     *
     * @param lorebookId 世界书 id。
     */
    @Query("DELETE FROM lorebook_entries WHERE lorebookId = :lorebookId")
    suspend fun deleteEntriesByLorebookId(lorebookId: Long)
}

/**
 * 按安全批次读取指定 ID 的世界书条目。
 *
 * Android SQLite 单条语句最多绑定 999 个参数；调用方通常使用该方法读取故事关联的
 * 全量条目，因此必须拆分列表，避免大规模世界书配置触发 SQLite bind 参数异常。
 * 返回顺序与 [getEntriesByIds] 的 `ORDER BY order, id` 保持一致。
 */
suspend fun LorebookEntryDao.getEntriesByIdsChunked(ids: List<Long>): List<LorebookEntry> {
    val distinctIds = ids.distinct()
    if (distinctIds.isEmpty()) return emptyList()
    return distinctIds
        .chunked(SQLITE_IN_QUERY_BATCH_SIZE)
        .flatMap { getEntriesByIds(it) }
        .sortedWith(compareBy<LorebookEntry> { it.order }.thenBy { it.id })
}

/** 为绑定参数留出余量，避免接近 SQLite 的 999 项上限。 */
private const val SQLITE_IN_QUERY_BATCH_SIZE = 900
