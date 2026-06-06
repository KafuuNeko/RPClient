package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.ChatSession

@Dao
interface ChatSessionDao : MutableDao<ChatSession> {
    /**
     * 获取所有会话。
     *
     * @return 按最近活跃时间倒序排列的会话列表。
     */
    @Query("SELECT * FROM chat_sessions ORDER BY latestTime DESC, id DESC")
    suspend fun getAllSessions(): List<ChatSession>

    /**
     * 根据角色 id 获取该角色下的所有会话。
     *
     * @param characterId 角色 id。
     * @return 按最近活跃时间倒序排列的会话列表。
     */
    @Query("SELECT * FROM chat_sessions WHERE characterId = :characterId ORDER BY latestTime DESC, id DESC")
    suspend fun getSessionsByCharacterId(characterId: Long): List<ChatSession>

    /**
     * 根据会话 id 查询会话。
     *
     * @param id 会话 id。
     * @return 匹配的会话；如果不存在则返回 null。
     */
    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): ChatSession?

    /**
     * 修改会话标题。
     *
     * @param id 会话 id。
     * @param title 新标题。
     */
    @Query("UPDATE chat_sessions SET title = :title WHERE id = :id")
    suspend fun updateSessionTitle(id: Long, title: String)

    /**
     * 修改会话最近活跃时间。
     *
     * @param id 会话 id。
     * @param latestTime 最近活跃时间。
     */
    @Query("UPDATE chat_sessions SET latestTime = :latestTime WHERE id = :id")
    suspend fun updateSessionLatestTime(id: Long, latestTime: Long)

    /**
     * 修改当前会话启用的世界书条目集。
     *
     * @param id 会话 id。
     * @param lorebookEntrySet 世界书条目 id 集合字符串。
     */
    @Query("UPDATE chat_sessions SET lorebookEntrySet = :lorebookEntrySet WHERE id = :id")
    suspend fun updateSessionLorebookEntrySet(id: Long, lorebookEntrySet: String)

    /**
     * 修改用户笔记。
     *
     * @param id 会话 id。
     * @param userNote 新的用户笔记。
     */
    @Query("UPDATE chat_sessions SET userNote = :userNote WHERE id = :id")
    suspend fun updateSessionUserNote(id: Long, userNote: String)

    /**
     * 修改角色笔记。
     *
     * @param id 会话 id。
     * @param creatorNotes 新的角色笔记。
     */
    @Query("UPDATE chat_sessions SET creatorNotes = :creatorNotes WHERE id = :id")
    suspend fun updateSessionCreatorNotes(id: Long, creatorNotes: String?)

    /**
     * 更新世界书运行时状态。
     *
     * 该字段由 prompt 构建过程写入，用于下一轮请求判断 sticky/cooldown 是否仍然有效。
     */
    @Query("UPDATE chat_sessions SET worldInfoStateJson = :worldInfoStateJson WHERE id = :id")
    suspend fun updateSessionWorldInfoState(id: Long, worldInfoStateJson: String)

    /**
     * 根据会话 id 删除会话。
     *
     * @param id 会话 id。
     */
    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)
}
