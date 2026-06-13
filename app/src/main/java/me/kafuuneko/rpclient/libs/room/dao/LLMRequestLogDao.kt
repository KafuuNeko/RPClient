package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog

/** 本地 LLM 调试日志的数据库访问接口。 */
@Dao
interface LLMRequestLogDao : MutableDao<LLMRequestLog> {
    /** 按时间倒序读取全部请求日志。 */
    @Query("SELECT * FROM llm_request_logs ORDER BY createTime DESC, id DESC")
    suspend fun getAllLogs(): List<LLMRequestLog>

    /** 清空全部调试日志。 */
    @Query("DELETE FROM llm_request_logs")
    suspend fun deleteAll()
}
