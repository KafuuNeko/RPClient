package me.kafuuneko.rpclient.libs.room.dao

import androidx.room.Dao
import androidx.room.Query
import me.kafuuneko.rpclient.libs.room.MutableDao
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageGroup
import me.kafuuneko.rpclient.libs.room.model.LLMTokenUsageSummary

/** 成功 LLM 请求 Token 用量的数据库访问接口。 */
@Dao
interface LLMTokenUsageDao : MutableDao<LLMTokenUsageRecord> {
    /** 汇总指定起始时间之后的请求数与输入、输出 Token。 */
    @Query(
        """
        SELECT COUNT(*) AS requestCount,
               COALESCE(SUM(inputTokens), 0) AS inputTokens,
               COALESCE(SUM(outputTokens), 0) AS outputTokens
        FROM llm_token_usage_records
        WHERE createTime >= :startTime
        """
    )
    suspend fun getSummary(startTime: Long): LLMTokenUsageSummary

    /** 按实际模型和 Host 聚合指定时间范围内的用量。 */
    @Query(
        """
        SELECT effectiveModel,
               apiHost,
               apiPort,
               COUNT(*) AS requestCount,
               COALESCE(SUM(inputTokens), 0) AS inputTokens,
               COALESCE(SUM(outputTokens), 0) AS outputTokens,
               MAX(createTime) AS latestTime
        FROM llm_token_usage_records
        WHERE createTime >= :startTime
        GROUP BY effectiveModel, apiHost, apiPort
        ORDER BY (COALESCE(SUM(inputTokens), 0) + COALESCE(SUM(outputTokens), 0)) DESC,
                 latestTime DESC
        """
    )
    suspend fun getGroups(startTime: Long): List<LLMTokenUsageGroup>

    /** 读取指定时间范围内最新的请求明细。 */
    @Query(
        """
        SELECT * FROM llm_token_usage_records
        WHERE createTime >= :startTime
        ORDER BY createTime DESC, id DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentRecords(startTime: Long, limit: Int): List<LLMTokenUsageRecord>

    /** 清空全部 Token 用量统计。 */
    @Query("DELETE FROM llm_token_usage_records")
    suspend fun deleteAll()
}
