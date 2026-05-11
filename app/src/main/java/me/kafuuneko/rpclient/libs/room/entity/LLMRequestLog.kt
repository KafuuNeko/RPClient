package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

@Entity(tableName = "llm_request_logs")
data class LLMRequestLog(
    // Log ID
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    // Provider 名称
    val providerName: String,
    // Provider 类型
    val providerType: LLMProviderType,
    // 请求协议
    val protocol: LLMProviderProtocol,
    // 模型名称
    val model: String,
    // 是否流式请求
    val isStreaming: Boolean,
    // 原始请求 JSON
    val requestJson: String,
    // 原始响应 JSON
    val responseJson: String
)
