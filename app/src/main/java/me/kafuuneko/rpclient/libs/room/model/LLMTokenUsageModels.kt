package me.kafuuneko.rpclient.libs.room.model

import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageRecord

/** 指定时间范围内的 Token 用量总览。 */
data class LLMTokenUsageSummary(
    /** 当前统计范围包含的模型请求次数。 */
    val requestCount: Long,
    /** 本次请求消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 本次请求生成的输出 Token 数。 */
    val outputTokens: Long,
    /** 命中缓存的输入 Token 数。 */
    val cachedInputTokens: Long = 0L,
    /** 服务端标记的推理 Token 数。 */
    val reasoningTokens: Long = 0L
)

/** 按本地自然日分组的 Token 用量统计项。 */
data class LLMTokenUsageDailyStat(
    /** 本地格式化的日期字符串（如 yyyy-MM-dd）。 */
    val dateText: String,
    /** 当日模型请求次数。 */
    val requestCount: Long,
    /** 当日消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 当日生成的输出 Token 数。 */
    val outputTokens: Long,
    /** 当日命中的缓存输入 Token 数。 */
    val cachedInputTokens: Long = 0L,
    /** 当日标记的推理 Token 数。 */
    val reasoningTokens: Long = 0L
)

/** 按实际模型与 API Host 聚合的 Token 用量。 */
data class LLMTokenUsageGroup(
    /** 模型服务实际执行请求时使用的模型名称。 */
    val effectiveModel: String,
    /** 脱敏后用于统计的模型服务主机名。 */
    val apiHost: String,
    /** 脱敏后用于统计的模型服务端口。 */
    val apiPort: Int,
    /** 当前统计范围包含的模型请求次数。 */
    val requestCount: Long,
    /** 本次请求消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 本次请求生成的输出 Token 数。 */
    val outputTokens: Long,
    /** 命中缓存的输入 Token 数。 */
    val cachedInputTokens: Long = 0L,
    /** 服务端标记的推理 Token 数。 */
    val reasoningTokens: Long = 0L,
    /** 相关记录最后更新或活动的时间戳，单位为毫秒。 */
    val latestTime: Long
)

/** 消耗统计页一次读取所需的总览、聚合、时序趋势与近期明细。 */
data class LLMTokenUsageDashboard(
    /** 当前时间范围的全局汇总数据。 */
    val summary: LLMTokenUsageSummary,
    /** 按自然日划分的趋势时序统计列表。 */
    val dailyStats: List<LLMTokenUsageDailyStat>,
    /** 当前页面或结果包含的分组列表。 */
    val groups: List<LLMTokenUsageGroup>,
    /** Token 用量页最近请求记录列表。 */
    val recentRecords: List<LLMTokenUsageRecord>
)

