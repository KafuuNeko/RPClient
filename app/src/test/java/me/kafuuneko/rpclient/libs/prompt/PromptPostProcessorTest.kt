package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptPostProcessorTest {
    @Test
    fun semiStrictKeepsMidPromptSystemAtItsOriginalPositionAsUser() {
        val request = request(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.User, "Hi"),
            LLMMessage(LLMMessageRole.Assistant, "Hello"),
            LLMMessage(LLMMessageRole.System, "Post")
        ).withPostProcessedMessages(PromptPostProcessingMode.SemiStrict, "[Start]")

        assertEquals(
            listOf(
                LLMMessage(LLMMessageRole.System, "Main"),
                LLMMessage(LLMMessageRole.User, "Hi"),
                LLMMessage(LLMMessageRole.Assistant, "Hello"),
                LLMMessage(LLMMessageRole.User, "Post")
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
        ).withPostProcessedMessages(
            PromptPostProcessingMode.SingleUserMessage,
            "[Start]",
            PromptPostProcessingNames(userName = "Alex", characterName = "Mina")
        )

        assertEquals(1, request.messages.size)
        assertEquals(LLMMessageRole.User, request.messages.single().role)
        assertEquals("Main\n\nMina: Greeting", request.messages.single().content)
    }

    @Test
    fun strictAddsPlaceholderWhenOnlySystemMessageExists() {
        val request = request(
            LLMMessage(LLMMessageRole.System, "Main")
        ).withPostProcessedMessages(PromptPostProcessingMode.Strict, "[Start]")

        assertEquals(
            listOf(
                LLMMessage(LLMMessageRole.System, "Main"),
                LLMMessage(LLMMessageRole.User, "[Start]")
            ),
            request.messages
        )
    }

    private fun request(vararg messages: LLMMessage): LLMGenerationRequest {
        return LLMGenerationRequest(messages = messages.toList())
    }
}
