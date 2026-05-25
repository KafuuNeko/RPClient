package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptPostProcessorTest {
    @Test
    fun semiStrictMovesAllSystemContentToLeadingMessage() {
        val request = request(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.User, "Hi"),
            LLMMessage(LLMMessageRole.Assistant, "Hello"),
            LLMMessage(LLMMessageRole.System, "Post")
        ).withPostProcessedMessages(PromptPostProcessingMode.SemiStrict, "[Start]")

        assertEquals(
            listOf(
                LLMMessage(LLMMessageRole.System, "Main\n\nPost"),
                LLMMessage(LLMMessageRole.User, "Hi"),
                LLMMessage(LLMMessageRole.Assistant, "Hello")
            ),
            request.messages
        )
    }

    @Test
    fun strictInsertsPlaceholderBeforeLeadingAssistantBody() {
        val request = request(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.Assistant, "Greeting")
        ).withPostProcessedMessages(PromptPostProcessingMode.Strict, "[Start]")

        assertEquals(
            listOf(
                LLMMessage(LLMMessageRole.System, "Main"),
                LLMMessage(LLMMessageRole.User, "[Start]"),
                LLMMessage(LLMMessageRole.Assistant, "Greeting")
            ),
            request.messages
        )
    }

    @Test
    fun singleUserMessageFlattensAllRoles() {
        val request = request(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.Assistant, "Greeting")
        ).withPostProcessedMessages(PromptPostProcessingMode.SingleUserMessage, "[Start]")

        assertEquals(1, request.messages.size)
        assertEquals(LLMMessageRole.User, request.messages.single().role)
        assertEquals("System:\nMain\n\nAssistant:\nGreeting", request.messages.single().content)
    }

    private fun request(vararg messages: LLMMessage): LLMGenerationRequest {
        return LLMGenerationRequest(messages = messages.toList())
    }
}
