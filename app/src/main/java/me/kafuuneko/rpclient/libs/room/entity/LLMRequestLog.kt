package me.kafuuneko.rpclient.libs.room.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/**
 * 调试模式下保存的原始 LLM 请求与响应。
 *
 * 记录可能包含 Prompt 或供应商返回内容，仅用于本地排障，不应在普通模式写入。
 */
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
