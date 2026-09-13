package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.model.GroupChatSessionOverview

/** 群聊会话基本信息的数据库访问接口。 */
@Dao
interface GroupChatSessionDao : MutableDao<GroupChatSession> {
    /** 按最近活跃时间读取全部群聊。 */
    @Query("SELECT * FROM group_chat_sessions ORDER BY latestTime DESC, id DESC")
    suspend fun getAllSessions(): List<GroupChatSession>

    /**
     * 一次读取首页所需的群聊成员名称、最后一条消息和消息数。
     *
     * 成员名称按群内顺序聚合；完整消息历史不会离开 SQLite。
     */
    @Query(
        """
        SELECT sessions.id,
               sessions.title,
               sessions.latestTime,
               COALESCE(
                   (
                       SELECT GROUP_CONCAT(orderedMembers.name, ', ')
                       FROM (
                           SELECT characters.name AS name
                           FROM group_chat_members AS members
                           INNER JOIN character AS characters
                               ON characters.id = members.characterId
                           WHERE members.sessionId = sessions.id
                           ORDER BY members.sortOrder ASC, members.characterId ASC
                       ) AS orderedMembers
                   ),
                   ''
               ) AS memberNames,
               (
                   SELECT CASE WHEN TRIM(messages.content) = '' AND EXISTS (
                       SELECT 1 FROM message_images AS images
                       WHERE images.messageId = messages.id AND images.messageType = 'Group'
                   ) THEN '[Image]' ELSE messages.content END
                   FROM group_chat_messages AS messages
                   WHERE messages.sessionId = sessions.id
                   ORDER BY messages.createTime DESC, messages.id DESC
                   LIMIT 1
               ) AS latestMessageContent,
               (
                   SELECT COUNT(*)
                   FROM group_chat_messages AS messages
                   WHERE messages.sessionId = sessions.id
               ) AS messageCount
        FROM group_chat_sessions AS sessions
        ORDER BY sessions.latestTime DESC, sessions.id DESC
        """
    )
    suspend fun getSessionOverviews(): List<GroupChatSessionOverview>

    /** 根据主键读取群聊会话。 */
    @Query("SELECT * FROM group_chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): GroupChatSession?

    /** 更新会话最近活跃时间。 */
    @Query("UPDATE group_chat_sessions SET latestTime = :latestTime WHERE id = :id")
    suspend fun updateLatestTime(id: Long, latestTime: Long)

    /** 删除会话；关联成员、消息和摘要由外键级联清理。 */
    @Query("DELETE FROM group_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
