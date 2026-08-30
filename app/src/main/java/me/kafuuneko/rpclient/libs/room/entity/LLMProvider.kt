package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_CONTEXT_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_MAX_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** 持久化的模型配置和默认生成参数。 */
@Entity(tableName = "llm_providers")
data class LLMProvider(
    // 模型配置主键
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 展示名称
    val name: String,
    // 供应商类型
    val providerType: LLMProviderType,
    // 实际调用协议
    val protocol: LLMProviderProtocol,
    // 接口基础地址
    val baseUrl: String,
    // API Key，留空表示尚未配置
    val apiKey: String = "",
    // 默认模型名
    val model: String,
    // 额外请求头 JSON
    val customHeadersJson: String = "",
    // 合并到协议请求体的 JSON Merge Patch；默认不改变基础请求。
    @ColumnInfo(defaultValue = "'{}'")
    val requestBodyPatchJson: String = "{}",
    // 默认 Temp
    val temperature: Float = 0.8f,
    // 默认 Top P
    val topP: Float = 1.0f,
    // 默认最大输出 Token
    val maxTokens: Int = DEFAULT_LLM_MAX_TOKENS,
    // 默认上下文 Token 预算
    val contextTokens: Int = DEFAULT_LLM_CONTEXT_TOKENS,
    // 代理 Tokenizer 的本地预算预留率，不会发送给模型服务。
    @ColumnInfo(defaultValue = "15")
    val tokenEstimateReservePercent: Int = DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT,
    // 是否在请求中显式发送 temperature。
    val sendTemperature: Boolean = true,
    // 是否在请求中显式发送 top_p。
    val sendTopP: Boolean = true,
    // 是否为 OpenAI-compatible 流式请求附加服务端用量返回选项。
    @ColumnInfo(defaultValue = "0")
    val requestStreamUsage: Boolean = false,
    // 当前模型配置独立使用的 Prompt 后处理模式 ordinal。
    val promptPostProcessingMode: Int = 0,
    // 是否启用
    val isEnabled: Boolean = true,
    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    // 更新时间
    val updateTime: Long = createTime
)

const val DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT = 15
const val MIN_TOKEN_ESTIMATE_RESERVE_PERCENT = 0
const val MAX_TOKEN_ESTIMATE_RESERVE_PERCENT = 50

/** 转换为网络适配器使用的不可变运行时配置。 */
fun LLMProvider.toConfig() = LLMProviderConfig(
    name = name,
    providerType = providerType,
    protocol = protocol,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    customHeadersJson = customHeadersJson,
    requestBodyPatchJson = requestBodyPatchJson,
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    contextTokens = contextTokens,
    sendTemperature = sendTemperature,
    sendTopP = sendTopP,
    requestStreamUsage = requestStreamUsage,
    providerId = id
)
