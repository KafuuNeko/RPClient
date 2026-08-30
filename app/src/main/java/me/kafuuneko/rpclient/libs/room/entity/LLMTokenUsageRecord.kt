package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** Token 数值的来源，用于避免把本地预估误解为服务商账单。 */
enum class LLMTokenUsageSource {
    ProviderReported,
    ModelAwareEstimate,
    ProxyEstimate
}

/**
 * 一次成功 LLM 生成请求的脱敏 Token 用量快照。
 *
 * - 只保存模型、Host、用量和耗时等统计元数据，不保存 URL 路径、鉴权信息或对话正文。
 * - 模型配置采用无外键快照，删除 Provider 后仍可保留历史统计。
 * - 输入和输出分别记录来源，支持服务端只上报一侧时对另一侧独立估算。
 */
@Entity(
    tableName = "llm_token_usage_records",
    indices = [
        Index(value = ["createTime"]),
        Index(value = ["effectiveModel", "apiHost", "apiPort", "createTime"])
    ]
)
data class LLMTokenUsageRecord(
    /** 用量记录主键，由数据库自动生成。 */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 请求成功完成并创建统计记录的时间戳，单位为毫秒。 */
    val createTime: Long = System.currentTimeMillis(),

    /** 关联的模型配置 ID；未保存的临时配置没有 ID，因此可以为空。 */
    val providerId: Long? = null,

    /** 请求发生时模型配置的显示名称快照。 */
    val providerName: String,

    /** 请求发生时模型供应商类型的快照。 */
    val providerType: LLMProviderType,

    /** 本次请求实际采用的 LLM 通信协议。 */
    val protocol: LLMProviderProtocol,

    /** API 地址的 Host，不包含协议、端口、路径、查询参数或鉴权信息。 */
    val apiHost: String,

    /** API 地址的有效端口；地址无法解析时使用内部未知端口值。 */
    val apiPort: Int,

    /** 客户端在请求中指定的模型名称。 */
    val requestedModel: String,

    /** 服务端响应的实际模型名称；未返回时回退为请求模型名称。 */
    val effectiveModel: String,

    /** 是否通过流式接口完成本次生成请求。 */
    val isStreaming: Boolean,

    /** 输入 Token 数，可能来自服务端上报或本地估算。 */
    val inputTokens: Long,

    /** 输出 Token 数，可能来自服务端上报或本地估算。 */
    val outputTokens: Long,

    /** 输入 Token 数值的来源。 */
    val inputTokenSource: LLMTokenUsageSource,

    /** 输出 Token 数值的来源。 */
    val outputTokenSource: LLMTokenUsageSource,

    /** 服务端上报的缓存输入 Token 数；服务端未提供时为空。 */
    val cachedInputTokens: Long? = null,

    /** 服务端上报的推理 Token 数；服务端未提供时为空。 */
    val reasoningTokens: Long? = null,

    /** 本地估算所使用的 Tokenizer 名称；输入和输出均由服务端上报时为空。 */
    val tokenizerName: String? = null,

    /** 从发起请求到成功消费完整响应的耗时，单位为毫秒。 */
    val durationMs: Long
)
