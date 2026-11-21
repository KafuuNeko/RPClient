package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary

/** 群聊摘要快照及覆盖边界的数据库访问接口。 */
@Dao
interface GroupChatSummaryDao : MutableDao<GroupChatSummary> {
    /** 读取指定摘要，供草稿保存时校验生成依赖。 */
    @Query("SELECT * FROM group_chat_summaries WHERE id = :id")
    suspend fun getById(id: Long): GroupChatSummary?

    /** 读取会话最新摘要快照。 */
    @Query(
        """
        SELECT * FROM group_chat_summaries
        WHERE sessionId = :sessionId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getLatest(sessionId: Long): GroupChatSummary?

    /**
     * 读取指定消息位置仍然有效的最新摘要，包括主动清空的零边界快照。
     *
     * @param sessionId 源群聊会话 ID。
     * @param throughMessageId 包含在分支内的最后一条消息 ID。
     * @return 覆盖边界位于稳定消息前缀内的最新摘要；不存在时返回 null。
     */
    @Query(
        """
        SELECT summaries.*
        FROM group_chat_summaries AS summaries
        JOIN group_chat_messages AS target
          ON target.id = :throughMessageId
         AND target.sessionId = summaries.sessionId
        LEFT JOIN group_chat_messages AS boundary
          ON boundary.id = summaries.coveredMessageId
         AND boundary.sessionId = summaries.sessionId
        WHERE summaries.sessionId = :sessionId
          AND (
              summaries.coveredMessageId = 0
              OR boundary.createTime < target.createTime
              OR (boundary.createTime = target.createTime AND boundary.id <= target.id)
          )
        ORDER BY summaries.id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestAtOrBefore(
        sessionId: Long,
        throughMessageId: Long,
    ): GroupChatSummary?

    /**
     * 按写入顺序读取覆盖位置更早的快照，包括用户主动清空的摘要。
     * @param sessionId 群聊会话 ID。
     * @param coveredMessageId 当前摘要的覆盖位置，候选位置必须严格早于此值。
     * @param beforeSummaryId 只读取此快照之前的历史，默认不限制快照顺序。
     * @return 可作为重新摘要基础的快照，不存在时返回 null。
     */
    @Query(
        """
        SELECT * FROM group_chat_summaries
        WHERE sessionId = :sessionId AND coveredMessageId < :coveredMessageId
          AND id < :beforeSummaryId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getPrevious(
        sessionId: Long,
        coveredMessageId: Long,
        beforeSummaryId: Long = Long.MAX_VALUE,
    ): GroupChatSummary?

    /** 按快照写入顺序读取上一份摘要。 */
    @Query(
        """
        SELECT * FROM group_chat_summaries
        WHERE sessionId = :sessionId AND id < :summaryId
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getPreviousById(
        sessionId: Long,
        summaryId: Long,
    ): GroupChatSummary?

    /** 原位更新摘要内容、覆盖边界和生成时间。 */
    @Query(
        """
        UPDATE group_chat_summaries
        SET content = :content, coveredMessageId = :coveredMessageId, createTime = :createTime
        WHERE id = :id
        """
    )
    suspend fun updateContent(
        id: Long,
        content: String,
        coveredMessageId: Long,
        createTime: Long,
    )

    /** 删除覆盖了已修改或删除消息的失效摘要。 */
    @Query(
        """
        DELETE FROM group_chat_summaries
        WHERE sessionId = :sessionId AND coveredMessageId >= :messageId
        """
    )
    suspend fun deleteCovering(sessionId: Long, messageId: Long)
}
