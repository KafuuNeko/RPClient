package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSummary

@Dao
interface GroupChatSummaryDao : MutableDao<GroupChatSummary> {
    @Query(
        """
        SELECT * FROM group_chat_summaries
        WHERE sessionId = :sessionId
        ORDER BY coveredMessageId DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatest(sessionId: Long): GroupChatSummary?

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

    @Query(
        """
        DELETE FROM group_chat_summaries
        WHERE sessionId = :sessionId AND coveredMessageId >= :messageId
        """
    )
    suspend fun deleteCovering(sessionId: Long, messageId: Long)
}
