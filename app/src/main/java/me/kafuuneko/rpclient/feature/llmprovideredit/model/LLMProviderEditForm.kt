package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

data class LLMProviderEditForm(
    val id: Long = 0L,
    val createTime: Long = System.currentTimeMillis(),
    val name: String = "",
    val providerType: LLMProviderType = LLMProviderType.Custom,
    val protocol: LLMProviderProtocol = LLMProviderProtocol.OpenAICompatible,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val customHeadersJson: String = "",
    val temperature: String = "0.8",
    val topP: String = "1.0",
    val maxTokens: String = "1200",
    val contextTokens: String = "8192",
    val isEnabled: Boolean = true
) {
    companion object {
        fun from(obj: LLMProvider) = LLMProviderEditForm(
            id = obj.id,
            createTime = obj.createTime,
            name = obj.name,
            providerType = obj.providerType,
            protocol = obj.protocol,
            baseUrl = obj.baseUrl,
            apiKey = obj.apiKey,
            model = obj.model,
            customHeadersJson = obj.customHeadersJson,
            temperature = obj.temperature.toString(),
            topP = obj.topP.toString(),
            maxTokens = obj.maxTokens.toString(),
            contextTokens = obj.contextTokens.toString(),
            isEnabled = obj.isEnabled
        )
    }

    fun toProviderOrNull(): LLMProvider? {
        val parsedTemperature = temperature.toFloatOrNull() ?: return null
        val parsedTopP = topP.toFloatOrNull() ?: return null
        val parsedMaxTokens = maxTokens.toIntOrNull() ?: return null
        val parsedContextTokens = contextTokens.toIntOrNull() ?: return null
        return LLMProvider(
            id = id,
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            customHeadersJson = customHeadersJson.trim(),
            temperature = parsedTemperature,
            topP = parsedTopP,
            maxTokens = parsedMaxTokens,
            contextTokens = parsedContextTokens,
            isEnabled = isEnabled,
            createTime = createTime
        )
    }
}
