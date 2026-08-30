package me.kafuuneko.rpclient.feature.tokenusage.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.entity.LLMTokenUsageSource

/** 消耗统计页顶部总览。 */
data class TokenUsageSummaryItem(
    /** 当前统计范围包含的模型请求次数。 */
    val requestCount: Long,
    /** 本次请求消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 本次请求生成的输出 Token 数。 */
    val outputTokens: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

/** 按实际模型和 API Host 聚合的展示项。 */
data class TokenUsageGroupItem(
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 脱敏后用于统计的模型服务端点。 */
    val endpoint: String,
    /** 当前统计范围包含的模型请求次数。 */
    val requestCount: Long,
    /** 本次请求消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 本次请求生成的输出 Token 数。 */
    val outputTokens: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}

/** 单次成功请求的近期明细展示项。 */
data class TokenUsageRecordItem(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 格式化后供界面展示的记录创建时间。 */
    val createTimeText: String,
    /** 请求发生时模型配置的显示名称快照。 */
    val providerName: String,
    /** 模型配置实际采用的通信协议。 */
    val protocol: LLMProviderProtocol,
    /** 当前配置或请求使用的模型名称。 */
    val model: String,
    /** 脱敏后用于统计的模型服务端点。 */
    val endpoint: String,
    /** 当前消息或请求是否处于流式生成状态。 */
    val isStreaming: Boolean,
    /** 本次请求消耗的输入 Token 数。 */
    val inputTokens: Long,
    /** 本次请求生成的输出 Token 数。 */
    val outputTokens: Long,
    /** 输入 Token 数值的统计来源。 */
    val inputSource: LLMTokenUsageSource,
    /** 输出 Token 数值的统计来源。 */
    val outputSource: LLMTokenUsageSource,
    /** 本次估算实际使用的 Tokenizer 名称。 */
    val tokenizerName: String?,
    /** 输入 Token 中由服务端缓存命中的数量。 */
    val cachedInputTokens: Long?,
    /** 输出 Token 中由服务端标记为推理过程的数量。 */
    val reasoningTokens: Long?,
    /** 本次操作持续的毫秒数。 */
    val durationMs: Long
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens
}
