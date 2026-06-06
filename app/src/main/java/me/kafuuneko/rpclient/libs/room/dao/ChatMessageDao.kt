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
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND source != 'Summary' ORDER BY createTime ASC, id ASC")
    suspend fun getMessagesBySessionId(sessionId: Long): List<ChatMessage>

    /**
     * 获取指定总结边界之后的普通消息。
     *
     * @param sessionId 会话 id。
     * @param coveredMessageId 总结覆盖到的最后一条普通消息 id；0 表示不跳过任何消息。
     * @return 按创建时间正序排列的未覆盖消息。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND id > :coveredMessageId
        ORDER BY createTime ASC, id ASC
        """
    )
    suspend fun getMessagesAfterId(sessionId: Long, coveredMessageId: Long): List<ChatMessage>

    /**
     * 获取指定会话最新写入的总结快照。
     *
     * @param sessionId 会话 id。
     * @return 最新总结快照；如果尚未生成总结则返回 null。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND source = 'Summary'
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSummaryBySessionId(sessionId: Long): ChatMessage?

    /**
     * 获取在指定普通消息位置仍然有效的最新总结快照。
     *
     * @param sessionId 会话 id。
     * @param messageId 分支或回溯位置的普通消息 id。
     * @return 覆盖边界不晚于指定消息的最新总结快照；不存在时返回 null。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source = 'Summary'
          AND coveredMessageId <= :messageId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSummaryAtOrBefore(sessionId: Long, messageId: Long): ChatMessage?

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
    @Query("SELECT COUNT(*) FROM chat_messages WHERE sessionId = :sessionId AND source != 'Summary'")
    suspend fun getMessageCountBySessionId(sessionId: Long): Int

    /**
     * 获取指定会话下的最后一条消息。
     *
     * @param sessionId 会话 id。
     * @return 最新消息；如果没有消息则返回 null。
     */
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId AND source != 'Summary' ORDER BY createTime DESC, id DESC LIMIT 1")
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
     * 删除所有覆盖了指定普通消息的总结快照。
     *
     * 普通消息被修改或删除后，依赖该消息及其后续历史的总结均不再可信。
     *
     * @param sessionId 会话 id。
     * @param messageId 被修改或删除的普通消息 id。
     */
    @Query(
        """
        DELETE FROM chat_messages
        WHERE sessionId = :sessionId
          AND source = 'Summary'
          AND coveredMessageId >= :messageId
        """
    )
    suspend fun deleteSummariesCoveringMessage(sessionId: Long, messageId: Long)

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
