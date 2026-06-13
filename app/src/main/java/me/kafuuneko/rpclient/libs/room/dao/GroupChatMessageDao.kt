package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage

/** 群聊消息的顺序查询、更新和截断删除接口。 */
@Dao
interface GroupChatMessageDao : MutableDao<GroupChatMessage> {
    /** 按创建时间和 ID 稳定读取完整群聊历史。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
        ORDER BY createTime ASC, id ASC
        """
    )
    suspend fun getMessages(sessionId: Long): List<GroupChatMessage>

    /** 读取指定消息边界之后的内容，供增量摘要使用。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId AND id > :messageId
        ORDER BY createTime ASC, id ASC
        """
    )
    suspend fun getMessagesAfterId(
        sessionId: Long,
        messageId: Long
    ): List<GroupChatMessage>

    /** 根据主键读取单条群聊消息。 */
    @Query("SELECT * FROM group_chat_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): GroupChatMessage?

    /** 读取会话最后一条消息。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestMessage(sessionId: Long): GroupChatMessage?

    /** 统计会话消息数量。 */
    @Query("SELECT COUNT(*) FROM group_chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    /** 原位更新消息正文。 */
    @Query("UPDATE group_chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    /** 删除单条消息。 */
    @Query("DELETE FROM group_chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 从指定消息起截断会话历史，用于重新生成。 */
    @Query("DELETE FROM group_chat_messages WHERE sessionId = :sessionId AND id >= :messageId")
    suspend fun deleteFrom(sessionId: Long, messageId: Long)
}
