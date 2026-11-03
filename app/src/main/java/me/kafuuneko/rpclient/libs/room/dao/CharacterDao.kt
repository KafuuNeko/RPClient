package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.model.CharacterContentItem
import me.kafuuneko.rpclient.libs.room.model.RecentCharacterOverview

/** 两个入口共用同一成员口径。候选故事角色与静音群成员都保留关联。 */
private const val CHARACTER_CONTENT = """
    SELECT characterId, 'Chat' AS kind, id AS contentId, title, latestTime FROM chat_sessions
    UNION ALL
    SELECT m.characterId, 'Group', g.id, g.title, g.latestTime
    FROM group_chat_members m JOIN group_chat_sessions g ON g.id = m.sessionId
    UNION ALL
    SELECT c.characterId, 'Story', s.id, s.title, s.latestTime
    FROM story_characters c JOIN stories s ON s.id = c.storyId
"""

/** 角色表的基础查询入口；跨表导入、删除和文件清理由 Repository 统一编排。 */
@Dao
interface CharacterDao : MutableDao<Character> {
    /**
     * 统计角色卡总数，角色浏览模式无需读取完整卡片。
     *
     * @return 角色卡总数。
     */
    @Query("SELECT COUNT(*) FROM character")
    suspend fun countCharacters(): Int

    /**
     * 读取角色名称，避免加载完整卡片。
     *
     * @param characterId 角色 ID。
     * @return 角色名称，角色不存在时返回 null。
     */
    @Query("SELECT name FROM character WHERE id = :characterId")
    suspend fun getCharacterName(characterId: Long): String?

    /**
     * 按最近活动聚合角色，单个 MAX 使标题取自该时间对应的一项内容。
     *
     * @return 按最近活动倒序排列的角色概览。
     */
    @Query(
        "SELECT a.characterId, c.name AS characterName, c.avatar AS avatarUuid, a.title AS latestTitle, " +
            "MAX(a.latestTime) AS latestTime, COUNT(*) AS contentCount " +
            "FROM (" + CHARACTER_CONTENT + ") a JOIN character c ON c.id = a.characterId " +
            "GROUP BY a.characterId ORDER BY MAX(a.latestTime) DESC, a.characterId DESC"
    )
    suspend fun getRecentCharacters(): List<RecentCharacterOverview>

    /**
     * 按稳定顺序读取指定角色的全部关联内容。
     *
     * @param characterId 角色 ID。
     * @return 关联的单聊、群聊与故事元数据。
     */
    @Query(
        "SELECT kind, contentId, title, latestTime FROM (" + CHARACTER_CONTENT + ") " +
            "WHERE characterId = :characterId ORDER BY latestTime DESC, kind, contentId DESC"
    )
    suspend fun getCharacterContent(characterId: Long): List<CharacterContentItem>

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

    /** 观察本地角色背景关联，删除角色后自动回退为空。 */
    @Query("SELECT backgroundFileUuid FROM character WHERE id = :id")
    fun observeBackground(id: Long): Flow<String?>
}
