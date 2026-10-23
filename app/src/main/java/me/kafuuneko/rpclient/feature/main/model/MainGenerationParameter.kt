package me.kafuuneko.rpclient.feature.main.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 设置页支持快速编辑的模型配置生成参数。 */
enum class MainGenerationParameter {
    Temperature,

    TopP,

    MaxTokens,

    ContextTokens;

    fun valueOf(provider: LLMProvider): String = when (this) {
        Temperature -> provider.temperature.toString()
        TopP -> provider.topP.toString()
        MaxTokens -> provider.maxTokens.toString()
        ContextTokens -> provider.contextTokens.toString()
    }

    fun updateProviderOrNull(provider: LLMProvider, rawValue: String): LLMProvider? {
        val value = rawValue.trim()
        val capabilities = LLMProviderCapabilities.forProtocol(provider.protocol)
        return when (this) {
            Temperature -> value.toFloatOrNull()
                ?.takeIf { it.isFinite() && (!provider.sendTemperature || it in capabilities.temperatureRange) }
                ?.let { provider.copy(temperature = it) }
            TopP -> value.toFloatOrNull()
                ?.takeIf { it.isFinite() && (!provider.sendTopP || it in capabilities.topPRange) }
                ?.let { provider.copy(topP = it) }
            MaxTokens -> value.toIntOrNull()
                ?.takeIf { it > 0 && it < provider.contextTokens }
                ?.let { provider.copy(maxTokens = it) }
            ContextTokens -> value.toIntOrNull()
                ?.takeIf { it > provider.maxTokens }
                ?.let { provider.copy(contextTokens = it) }
        }
    }

}
