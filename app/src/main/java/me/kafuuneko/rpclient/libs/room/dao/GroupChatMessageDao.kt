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

    /** 从会话末尾开始读取一页群聊消息，Repository 会把结果恢复为展示正序。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getLatestMessagePage(
        sessionId: Long,
        limit: Int
    ): List<GroupChatMessage>

    /** 使用创建时间和消息 ID 组成的稳定游标向前读取一页群聊消息。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
          AND (
              createTime < :beforeCreateTime
              OR (createTime = :beforeCreateTime AND id < :beforeMessageId)
          )
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getMessagePageBefore(
        sessionId: Long,
        beforeCreateTime: Long,
        beforeMessageId: Long,
        limit: Int
    ): List<GroupChatMessage>

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

    /** 统计摘要边界之后尚未覆盖的群聊消息数量。 */
    @Query(
        """
        SELECT COUNT(*) FROM group_chat_messages
        WHERE sessionId = :sessionId AND id > :messageId
        """
    )
    suspend fun getMessageCountAfterId(sessionId: Long, messageId: Long): Int

    /** 读取摘要边界之后按稳定顺序排列的最后一条群聊消息。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId AND id > :messageId
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestMessageAfterId(
        sessionId: Long,
        messageId: Long
    ): GroupChatMessage?

    /**
     * 读取摘要边界之后、最新消息之前的最早候选窗口。
     *
     * 最新消息由 Repository 单独附加，确保 Builder 继续按旧规则将其排除。
     */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId
          AND id > :messageId
          AND (
              createTime < :latestCreateTime
              OR (createTime = :latestCreateTime AND id < :latestMessageId)
          )
        ORDER BY createTime ASC, id ASC
        LIMIT :limit
        """
    )
    suspend fun getFirstMessagesBeforeLatestAfterId(
        sessionId: Long,
        messageId: Long,
        latestCreateTime: Long,
        latestMessageId: Long,
        limit: Int
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

    /** 读取会话最后一条用户消息，作为发言池的轮次边界。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId AND source = 'User'
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestUserMessage(sessionId: Long): GroupChatMessage?

    /** 读取会话最后一条角色消息，供连续发言限制使用。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId AND source = 'Character'
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCharacterMessage(sessionId: Long): GroupChatMessage?

    /**
     * 判断指定群聊的完整历史中是否存在角色消息。
     *
     * @param sessionId 群聊会话 ID。
     * @return 存在至少一条角色消息时返回 true。
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM group_chat_messages
            WHERE sessionId = :sessionId AND source = 'Character'
        )
        """
    )
    suspend fun hasCharacterMessage(sessionId: Long): Boolean

    /** 读取会话最后一条非系统消息，供空输入触发生成时使用。 */
    @Query(
        """
        SELECT * FROM group_chat_messages
        WHERE sessionId = :sessionId AND source != 'System'
        ORDER BY createTime DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestNonSystemMessage(sessionId: Long): GroupChatMessage?

    /** 读取整个会话出现过的非空发言者 ID 集合。 */
    @Query(
        """
        SELECT DISTINCT speakerCharacterId FROM group_chat_messages
        WHERE sessionId = :sessionId
          AND speakerCharacterId IS NOT NULL
        """
    )
    suspend fun getSpeakerIds(sessionId: Long): List<Long>

    /** 读取稳定消息边界之后出现过的非空发言者 ID 集合。 */
    @Query(
        """
        SELECT DISTINCT speakerCharacterId FROM group_chat_messages
        WHERE sessionId = :sessionId
          AND speakerCharacterId IS NOT NULL
          AND (
              createTime > :afterCreateTime
              OR (createTime = :afterCreateTime AND id > :afterMessageId)
          )
        """
    )
    suspend fun getSpeakerIdsAfter(
        sessionId: Long,
        afterCreateTime: Long,
        afterMessageId: Long
    ): List<Long>

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
