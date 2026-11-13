package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.model.GroupChatSessionOverview
import me.kafuuneko.rpclient.libs.room.model.MessageType

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
     * 图片存在性独立返回，由展示层决定本地化预览。
     *
     * @param messageType 使用稳定编码绑定的消息类型，须与当前会话表对应。
     * @return 包含最新消息正文、图片存在性和消息数的会话概览。
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
                   SELECT messages.content
                   FROM group_chat_messages AS messages
                   WHERE messages.sessionId = sessions.id
                   ORDER BY messages.createTime DESC, messages.id DESC
                   LIMIT 1
               ) AS latestMessageContent,
               EXISTS (
                   SELECT 1 FROM message_images AS images
                   WHERE images.messageType = :messageType
                     AND images.messageId = (
                       SELECT messages.id FROM group_chat_messages AS messages
                       WHERE messages.sessionId = sessions.id
                       ORDER BY messages.createTime DESC, messages.id DESC
                       LIMIT 1
                     )
               ) AS latestMessageHasImages,
               (
                   SELECT COUNT(*)
                   FROM group_chat_messages AS messages
                   WHERE messages.sessionId = sessions.id
               ) AS messageCount
        FROM group_chat_sessions AS sessions
        ORDER BY sessions.latestTime DESC, sessions.id DESC
        """
    )
    suspend fun getSessionOverviews(messageType: MessageType): List<GroupChatSessionOverview>

    /** 根据主键读取群聊会话。 */
    @Query("SELECT * FROM group_chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): GroupChatSession?

    /** 更新会话最近活跃时间。 */
    @Query("UPDATE group_chat_sessions SET latestTime = :latestTime WHERE id = :id")
    suspend fun updateLatestTime(id: Long, latestTime: Long)

    /** 只更新世界书配置，保留并发修改的人设绑定。 */
    @Query("UPDATE group_chat_sessions SET lorebookEntrySet = :value WHERE id = :id")
    suspend fun updateSessionLorebookEntryIds(id: Long, value: String)

    /** 只更新世界书配置，保留并发修改的人设绑定。 */
    @Query("UPDATE group_chat_sessions SET worldInfoStateJson = :value WHERE id = :id")
    suspend fun updateWorldInfoState(id: Long, value: String)

    /** 删除会话；关联成员、消息和摘要由外键级联清理。 */
    @Query("DELETE FROM group_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
