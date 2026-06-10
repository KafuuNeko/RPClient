package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession

@Dao
interface GroupChatSessionDao : MutableDao<GroupChatSession> {
    @Query("SELECT * FROM group_chat_sessions ORDER BY latestTime DESC, id DESC")
    suspend fun getAllSessions(): List<GroupChatSession>

    @Query("SELECT * FROM group_chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): GroupChatSession?

    @Query("UPDATE group_chat_sessions SET latestTime = :latestTime WHERE id = :id")
    suspend fun updateLatestTime(id: Long, latestTime: Long)

    @Query("DELETE FROM group_chat_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
