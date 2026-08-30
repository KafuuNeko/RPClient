package me.kafuuneko.rpclient.feature.tokenusage.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource

/** 消耗统计页顶部总览。 */
data class TokenUsageSummaryItem(
    val requestCount: Long,
    val inputTokens: Long,
    val outputTokens: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

/** 按实际模型和 API Host 聚合的展示项。 */
data class TokenUsageGroupItem(
    val model: String,
    val endpoint: String,
    val requestCount: Long,
    val inputTokens: Long,
    val outputTokens: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

/** 单次成功请求的近期明细展示项。 */
data class TokenUsageRecordItem(
    val id: Long,
    val createTimeText: String,
    val providerName: String,
    val protocol: LLMProviderProtocol,
    val model: String,
    val endpoint: String,
    val isStreaming: Boolean,
    val inputTokens: Long,
    val outputTokens: Long,
    val inputSource: LLMTokenUsageSource,
    val outputSource: LLMTokenUsageSource,
    val tokenizerName: String?,
    val cachedInputTokens: Long?,
    val reasoningTokens: Long?,
    val durationMs: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}
