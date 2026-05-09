package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

@Dao
interface ChatMessageDao : MutableDao<ChatMessage> {
    /**
     * 获取指定会话下的全部消息。
     *
     * @param sessionId 会话 id。
     * @return 按创建时间正序排列的消息列表。
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createTime ASC, id ASC")
    suspend fun getMessagesBySessionId(sessionId: Long): List<ChatMessage>

    /**
     * 获取指定会话下尚未纳入总结的消息。
     *
     * @param sessionId 会话 id。
     * @return 按创建时间正序排列的消息列表。
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND isSummarized = 0 ORDER BY createTime ASC, id ASC")
    suspend fun getUnsummarizedMessagesBySessionId(sessionId: Long): List<ChatMessage>

    /**
     * 根据消息 id 查询消息。
     *
     * @param id 消息 id。
     * @return 匹配的消息；如果不存在则返回 null。
     */
    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): ChatMessage?

    /**
     * 获取指定会话下的消息数量。
     *
     * @param sessionId 会话 id。
     * @return 消息数量。
     */
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCountBySessionId(sessionId: Long): Int

    /**
     * 获取指定会话下的最后一条消息。
     *
     * @param sessionId 会话 id。
     * @return 最新消息；如果没有消息则返回 null。
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createTime DESC, id DESC LIMIT 1")
    suspend fun getLatestMessageBySessionId(sessionId: Long): ChatMessage?

    /**
     * 修改消息正文。
     *
     * @param id 消息 id。
     * @param content 新的消息正文。
     */
    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    /**
     * 修改消息是否已被总结。
     *
     * @param id 消息 id。
     * @param isSummarized 是否已被总结。
     */
    @Query("UPDATE chat_messages SET isSummarized = :isSummarized WHERE id = :id")
    suspend fun updateMessageSummarized(id: Long, isSummarized: Boolean)

    /**
     * 批量标记消息已被总结。
     *
     * @param ids 消息 id 列表。
     */
    @Query("UPDATE chat_messages SET isSummarized = 1 WHERE id IN (:ids)")
    suspend fun markMessagesSummarized(ids: List<Long>)

    /**
     * 根据消息 id 删除消息。
     *
     * @param id 消息 id。
     */
    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    /**
     * 删除指定会话下的全部消息。
     *
     * @param sessionId 会话 id。
     */
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)
}
