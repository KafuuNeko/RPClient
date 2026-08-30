package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord

/** 指定时间范围内的 Token 用量总览。 */
data class LLMTokenUsageSummary(
    val requestCount: Long,
    val inputTokens: Long,
    val outputTokens: Long
)

/** 按实际模型与 API Host 聚合的 Token 用量。 */
data class LLMTokenUsageGroup(
    val effectiveModel: String,
    val apiHost: String,
    val apiPort: Int,
    val requestCount: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val latestTime: Long
)

/** 消耗统计页一次读取所需的总览、聚合与近期明细。 */
data class LLMTokenUsageDashboard(
    val summary: LLMTokenUsageSummary,
    val groups: List<LLMTokenUsageGroup>,
    val recentRecords: List<LLMTokenUsageRecord>
)
