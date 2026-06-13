package me.kafuuneko.rpclient.libs.llm.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LLMGenerationOptionsTest {
    @Test
    fun disabledSamplingParametersAreOmittedFromResolvedOptions() {
        val provider = LLMProviderConfig(
            name = "Claude",
            providerType = LLMProviderType.Claude,
            protocol = LLMProviderProtocol.AnthropicMessages,
            baseUrl = "https://example.com",
            model = "claude",
            sendTemperature = true,
            sendTopP = false
        )

        val resolved = LLMGenerationOptions(
            temperature = 0.4f,
            topP = 0.7f,
            maxTokens = 256
        ).resolveFor(provider)

        assertEquals(0.4f, resolved.temperature)
        assertNull(resolved.topP)
        assertEquals(256, resolved.maxTokens)
    }

    @Test
    fun enabledParametersFallBackToProviderDefaults() {
        val provider = LLMProviderConfig(
            name = "OpenAI",
            providerType = LLMProviderType.ChatGPT,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.com",
            model = "model",
            temperature = 0.6f,
            topP = 0.8f
        )

        val resolved = LLMGenerationOptions().resolveFor(provider)

        assertEquals(0.6f, resolved.temperature)
        assertEquals(0.8f, resolved.topP)
        assertEquals(provider.maxTokens, resolved.maxTokens)
    }
}
