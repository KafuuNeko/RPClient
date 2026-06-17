package me.kafuuneko.rpclient.libs.llm.adapter

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAICompatibleLLMClientTest {
    @Test
    fun cleanContentStringPreservesLeadingWhitespace() {
        assertEquals(" world", cleanContentString(" world"))
    }

    @Test
    fun cleanContentStringTreatsNullLiteralAsEmpty() {
        assertEquals("", cleanContentString("null"))
    }
}
