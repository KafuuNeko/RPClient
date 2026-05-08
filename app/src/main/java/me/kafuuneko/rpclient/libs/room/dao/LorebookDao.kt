package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Lorebook

@Dao
interface LorebookDao : MutableDao<Lorebook> {
    /**
     * 获取所有世界书。
     *
     * @return 按 id 倒序排列的世界书列表。
     */
    @Query("SELECT * FROM lorebooks ORDER BY id DESC")
    suspend fun getAllLorebooks(): List<Lorebook>

    /**
     * 根据世界书 id 查询世界书。
     *
     * @param id 世界书 id。
     * @return 匹配的世界书；如果不存在则返回 null。
     */
    @Query("SELECT * FROM lorebooks WHERE id = :id")
    suspend fun getLorebookById(id: Long): Lorebook?

    /**
     * 修改世界书名称。
     *
     * @param id 世界书 id。
     * @param name 新的世界书名称。
     */
    @Query("UPDATE lorebooks SET name = :name WHERE id = :id")
    suspend fun updateLorebookName(id: Long, name: String)

    /**
     * 根据世界书 id 删除世界书。
     *
     * @param id 世界书 id。
     */
    @Query("DELETE FROM lorebooks WHERE id = :id")
    suspend fun deleteLorebookById(id: Long)
}
