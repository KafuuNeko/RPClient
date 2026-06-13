package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

/** 群聊会话基本信息的数据库访问接口。 */
@Dao
interface GroupChatSessionDao : MutableDao<GroupChatSession> {
    /** 按最近活跃时间读取全部群聊。 */
    @Query("SELECT * FROM group_chat_sessions ORDER BY latestTime DESC, id DESC")
    suspend fun getAllSessions(): List<GroupChatSession>

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
