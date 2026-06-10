package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Character

@Dao
interface CharacterDao : MutableDao<Character> {
    /**
     * 获取所有角色。
     *
     * @return 按 id 倒序排列的角色列表。
     */
    @Query("SELECT * FROM character ORDER BY id DESC")
    suspend fun getAllCharacters(): List<Character>

    /**
     * 根据角色 id 查询角色。
     *
     * @param id 角色 id。
     * @return 匹配的角色；如果不存在则返回 null。
     */
    @Query("SELECT * FROM character WHERE id = :id")
    suspend fun getCharacterById(id: Long): Character?

    /**
     * 根据角色 id 删除角色。
     *
     * @param id 角色 id。
     */
    @Query("DELETE FROM character WHERE id = :id")
    suspend fun deleteCharacterById(id: Long)

    /**
     * 清除所有绑定到指定世界书的角色关联。
     *
     * @param lorebookId 已删除的世界书 id。
     */
    @Query("UPDATE character SET characterLorebookId = 0 WHERE characterLorebookId = :lorebookId")
    suspend fun clearLorebookAssociations(lorebookId: Long)

}
