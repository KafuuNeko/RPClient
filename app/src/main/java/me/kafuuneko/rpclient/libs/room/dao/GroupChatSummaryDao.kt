package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary

/** 群聊摘要快照及覆盖边界的数据库访问接口。 */
@Dao
interface GroupChatSummaryDao : MutableDao<GroupChatSummary> {
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

    /** 读取当前覆盖边界之前可继续继承的上一份摘要。 */
    @Query(
        """
        SELECT * FROM group_chat_summaries
        WHERE sessionId = :sessionId AND coveredMessageId < :coveredMessageId
        ORDER BY coveredMessageId DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getPrevious(
        sessionId: Long,
        coveredMessageId: Long
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
        summaryId: Long
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
        createTime: Long
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
