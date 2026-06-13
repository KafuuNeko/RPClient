package me.kafuuneko.rpclient.feature.llmprovideredit.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode

class LLMProviderEditFormTest {
    @Test
    fun acceptsPositiveResponseBudgetSmallerThanContext() {
        assertNotNull(validForm().toProviderOrNull())
    }

    @Test
    fun rejectsNonPositiveOrExhaustedPromptBudget() {
        assertNull(validForm().copy(maxTokens = "0").toProviderOrNull())
        assertNull(validForm().copy(contextTokens = "0").toProviderOrNull())
        assertNull(
            validForm()
                .copy(maxTokens = "4096", contextTokens = "4096")
                .toProviderOrNull()
        )
        assertNull(
            validForm()
                .copy(maxTokens = "4097", contextTokens = "4096")
                .toProviderOrNull()
        )
    }

    @Test
    fun validatesEnabledSamplingParametersAgainstProtocolRange() {
        assertNull(
            validForm()
                .copy(
                    protocol = LLMProviderProtocol.AnthropicMessages,
                    temperature = "1.5",
                    sendTemperature = true
                )
                .toProviderOrNull()
        )
        assertNotNull(
            validForm()
                .copy(
                    protocol = LLMProviderProtocol.AnthropicMessages,
                    temperature = "1.5",
                    sendTemperature = false
                )
                .toProviderOrNull()
        )
        assertNull(validForm().copy(topP = "1.1", sendTopP = true).toProviderOrNull())
    }

    @Test
    fun roundTripsCapabilityFlagsAndProviderPostProcessing() {
        val provider = validForm().copy(
            sendTemperature = false,
            sendTopP = true,
            promptPostProcessingMode = PromptPostProcessingMode.SemiStrict
        ).toProviderOrNull() ?: error("Provider should be valid")

        val restored = LLMProviderEditForm.from(provider)

        assertEquals(false, restored.sendTemperature)
        assertEquals(true, restored.sendTopP)
        assertEquals(PromptPostProcessingMode.SemiStrict, restored.promptPostProcessingMode)
    }

    private fun validForm(): LLMProviderEditForm {
        return LLMProviderEditForm(
            name = "Test",
            baseUrl = "https://example.com",
            model = "model",
            maxTokens = "512",
            contextTokens = "4096"
        )
    }
}
