package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.model.LLMRequestLogOverview

/** 本地 LLM 调试日志的数据库访问接口。 */
@Dao
interface LLMRequestLogDao : MutableDao<LLMRequestLog> {
    /** 按时间倒序读取列表所需的轻量字段，原始载荷只返回固定长度前缀。 */
    @Query(
        """
        SELECT id,
               createTime,
               providerName,
               protocol,
               model,
               isStreaming,
               SUBSTR(requestJson, 1, :previewLength) AS requestPreview,
               SUBSTR(responseJson, 1, :previewLength) AS responsePreview
        FROM llm_request_logs
        ORDER BY createTime DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getLogOverviews(
        previewLength: Int,
        limit: Int,
        offset: Int,
    ): List<LLMRequestLogOverview>

    /** 按 ID 读取复制或查看所需的完整请求 JSON。 */
    @Query("SELECT requestJson FROM llm_request_logs WHERE id = :id")
    suspend fun getRequestJson(id: Long): String?

    /** 按 ID 读取复制或查看所需的完整响应 JSON。 */
    @Query("SELECT responseJson FROM llm_request_logs WHERE id = :id")
    suspend fun getResponseJson(id: Long): String?

    /** 清空全部调试日志。 */
    @Query("DELETE FROM llm_request_logs")
    suspend fun deleteAll()
}
