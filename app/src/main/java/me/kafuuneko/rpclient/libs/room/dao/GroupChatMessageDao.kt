package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage

@Dao
interface GroupChatMessageDao : MutableDao<GroupChatMessage> {
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
        ORDER BY createTime ASC, id ASC
        """
    )
    suspend fun getMessages(sessionId: Long): List<GroupChatMessage>

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

    @Query("SELECT * FROM group_chat_messages WHERE id = :id")
    suspend fun getMessageById(id: Long): GroupChatMessage?

    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestMessage(sessionId: Long): GroupChatMessage?

    @Query("SELECT COUNT(*) FROM group_chat_messages WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: Long): Int

    @Query("UPDATE group_chat_messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("DELETE FROM group_chat_messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM group_chat_messages WHERE sessionId = :sessionId AND id >= :messageId")
    suspend fun deleteFrom(sessionId: Long, messageId: Long)
}
