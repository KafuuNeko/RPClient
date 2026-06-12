package me.kafuuneko.rpclient.libs.room.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LLMProviderDefaultsTest {
    @Test
    fun currentProviderTemplatesUseSupportedModelIds() {
        val providers = createDefaultLLMProviders(now = 123L).associateBy { it.name }

        assertEquals(DEFAULT_GEMINI_MODEL, providers.getValue("Gemini").model)
        assertEquals(DEFAULT_CLAUDE_MODEL, providers.getValue("Claude").model)
        assertEquals(DEFAULT_OPENROUTER_MODEL, providers.getValue("OpenRouter").model)
        assertFalse(providers.values.any { it.isEnabled })
        assertEquals(setOf(123L), providers.values.map { it.createTime }.toSet())
        assertEquals(setOf(123L), providers.values.map { it.updateTime }.toSet())
    }
}
