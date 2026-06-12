package me.kafuuneko.rpclient.feature.llmprovideredit.model

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
