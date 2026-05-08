package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

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
     * 根据条目 id 删除世界书条目。
     *
     * @param id 世界书条目 id。
     */
    @Query("DELETE FROM lorebook_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

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
