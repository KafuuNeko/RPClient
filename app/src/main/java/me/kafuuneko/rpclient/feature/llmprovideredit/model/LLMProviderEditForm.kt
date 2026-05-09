package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

data class LLMProviderEditForm(
    val id: Long = 0L,
    val isSelected: Boolean = false,
    val createTime: Long = System.currentTimeMillis(),
    val name: String = "",
    val providerType: LLMProviderType = LLMProviderType.Custom,
    val protocol: LLMProviderProtocol = LLMProviderProtocol.OpenAICompatible,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val customHeadersJson: String = "",
    val temperature: String = "0.8",
    val maxTokens: String = "1200",
    val contextTokens: String = "8192",
    val isEnabled: Boolean = true
)
