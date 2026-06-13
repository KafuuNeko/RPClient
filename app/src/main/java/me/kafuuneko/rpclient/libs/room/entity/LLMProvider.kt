package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** 持久化的 LLM Provider 配置和默认生成参数。 */
@Entity(tableName = "llm_providers")
data class LLMProvider(
    // Provider 主键
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
    // 默认 Temp
    val temperature: Float = 0.8f,
    // 默认 Top P
    val topP: Float = 1.0f,
    // 默认最大输出 Token
    val maxTokens: Int = 1200,
    // 默认上下文 Token 预算
    val contextTokens: Int = 8192,
    // 是否启用
    val isEnabled: Boolean = true,
    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    // 更新时间
    val updateTime: Long = createTime
)

/** 转换为网络适配器使用的不可变运行时配置。 */
fun LLMProvider.toConfig() = LLMProviderConfig(
    name = name,
    providerType = providerType,
    protocol = protocol,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    customHeadersJson = customHeadersJson,
    temperature = temperature,
    topP = topP,
    maxTokens = maxTokens,
    contextTokens = contextTokens
)
