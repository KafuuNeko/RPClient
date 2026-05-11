package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog

@Dao
interface LLMRequestLogDao : MutableDao<LLMRequestLog> {
    @Query("SELECT * FROM llm_request_logs ORDER BY createTime DESC, id DESC")
    suspend fun getAllLogs(): List<LLMRequestLog>

    @Query("DELETE FROM llm_request_logs")
    suspend fun deleteAll()
}
