package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage

/**
 * 单聊普通消息与摘要快照的数据库访问接口。
 *
 * 本接口只表达查询边界；消息、摘要与会话元数据的一致提交必须由 ChatRepository 事务完成。
 */
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
     * 从会话末尾开始读取一页普通消息。
     *
     * 查询保持倒序以利用索引快速定位最新记录，Repository 会在返回业务层前恢复为正序。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND source != 'Summary'
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestMessagePageBySessionId(
        sessionId: Long,
        limit: Int
    ): List<ChatMessage>

    /**
     * 读取稳定游标之前的一页普通消息。
     *
     * 创建时间相同时使用消息 ID 继续排序，避免跨页重复或遗漏导入的同时间消息。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND (
              createTime < :beforeCreateTime
              OR (createTime = :beforeCreateTime AND id < :beforeMessageId)
          )
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getMessagePageBeforeBySessionId(
        sessionId: Long,
        beforeCreateTime: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<ChatMessage>

    /**
     * 按稳定的创建时间与主键顺序分页读取普通消息。
     *
     * 使用键集边界而不是 OFFSET，避免大型会话越往后扫描成本越高；调用方需要在同一读取
     * 事务中遍历所有页面，才能得到一致快照。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND (
              createTime > :afterCreateTime
              OR (createTime = :afterCreateTime AND id > :afterMessageId)
          )
        ORDER BY createTime ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getMessagePageBySessionId(
        sessionId: Long,
        afterCreateTime: Long,
        afterMessageId: Long,
        limit: Int
    ): List<ChatMessage>

    /**
     * 获取普通消息在稳定导出顺序中的零基位置。
     *
     * 消息不存在、不属于指定会话或是总结时返回 null。
     */
    @Query(
        """
        SELECT (
            SELECT COUNT(*)
            FROM chat_messages AS candidate
            WHERE candidate.sessionId = boundary.sessionId
              AND candidate.source != 'Summary'
              AND (
                  candidate.createTime < boundary.createTime
                  OR (
                      candidate.createTime = boundary.createTime
                      AND candidate.id < boundary.id
                  )
              )
        )
        FROM chat_messages AS boundary
        WHERE boundary.id = :messageId
          AND boundary.sessionId = :sessionId
          AND boundary.source != 'Summary'
        LIMIT 1
        """
    )
    suspend fun getMessageIndexBySessionId(sessionId: Long, messageId: Long): Int?

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

    /** 从会话末尾读取总结边界之后的有限普通消息窗口。 */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND id > :coveredMessageId
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestMessagesAfterId(
        sessionId: Long,
        coveredMessageId: Long,
        limit: Int
    ): List<ChatMessage>

    /**
     * 获取两个消息边界之间的普通消息。
     *
     * @param sessionId 会话 id。
     * @param afterMessageId 起始边界，该消息本身不包含在结果中。
     * @param throughMessageId 结束边界，该消息包含在结果中。
     * @return 按创建时间正序排列的普通消息。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND id > :afterMessageId
          AND id <= :throughMessageId
        ORDER BY createTime ASC, id ASC
        """
    )
    suspend fun getMessagesInRange(
        sessionId: Long,
        afterMessageId: Long,
        throughMessageId: Long
    ): List<ChatMessage>

    /** 从范围末尾读取有限普通消息窗口，供重生成回退总结边界时使用。 */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source != 'Summary'
          AND id > :afterMessageId
          AND id <= :throughMessageId
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestMessagesInRange(
        sessionId: Long,
        afterMessageId: Long,
        throughMessageId: Long,
        limit: Int
    ): List<ChatMessage>

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
     * 获取覆盖边界严格早于指定位置的最新总结。
     *
     * @param sessionId 会话 id。
     * @param coveredMessageId 当前总结的覆盖边界。
     * @return 可作为重新总结基础的上一条总结；不存在时返回 null。
     */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source = 'Summary'
          AND coveredMessageId < :coveredMessageId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getPreviousSummaryBeforeBoundary(
        sessionId: Long,
        coveredMessageId: Long
    ): ChatMessage?

    /** 按快照写入顺序读取上一份总结，供“恢复上一版”使用。 */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId
          AND source = 'Summary'
          AND id < :summaryId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getPreviousSummaryBeforeId(
        sessionId: Long,
        summaryId: Long
    ): ChatMessage?

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

    /** 获取指定会话最后一条角色消息。 */
    @Query(
        """
        SELECT * FROM chat_messages
        WHERE sessionId = :sessionId AND source = 'Char'
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCharacterMessageBySessionId(sessionId: Long): ChatMessage?

    /**
     * 修改消息正文。
     *
     * @param id 消息 id。
     * @param content 新的消息正文。
     */
    @Query("UPDATE chat_messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    /**
     * 更新总结快照的正文和实际覆盖边界。
     *
     * @param id 总结快照 id。
     * @param content 新的总结正文。
     * @param coveredMessageId 本次请求实际覆盖到的最后一条普通消息 id。
     */
    @Query(
        """
        UPDATE chat_messages
        SET content = :content, coveredMessageId = :coveredMessageId
        WHERE id = :id AND source = 'Summary'
        """
    )
    suspend fun updateSummary(id: Long, content: String, coveredMessageId: Long)

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
