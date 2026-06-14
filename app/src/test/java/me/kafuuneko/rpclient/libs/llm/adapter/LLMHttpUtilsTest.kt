package me.kafuuneko.rpclient.libs.llm.adapter

import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import org.junit.Assert.assertEquals
import org.junit.Test

class LLMHttpUtilsTest {
    @Test
    fun alternatingConversationKeepsMidPromptSystemPositionAndMergesUserTurns() {
        val result = listOf(
            LLMMessage(LLMMessageRole.System, "Main"),
            LLMMessage(LLMMessageRole.User, "Hello"),
            LLMMessage(LLMMessageRole.System, "Author note"),
            LLMMessage(LLMMessageRole.User, "Continue"),
            LLMMessage(LLMMessageRole.Assistant, "Reply")
        ).toAlternatingConversationMessages()

        assertEquals(
            listOf(
                LLMMessage(
                    LLMMessageRole.User,
                    "Hello\n\nAuthor note\n\nContinue"
                ),
                LLMMessage(LLMMessageRole.Assistant, "Reply")
            ),
            result
        )
    }

    @Test
    fun alternatingConversationAddsUserPlaceholderWhenOnlySystemExists() {
        val result = listOf(
            LLMMessage(LLMMessageRole.System, "Main")
        ).toAlternatingConversationMessages()

        assertEquals(
            listOf(LLMMessage(LLMMessageRole.User, "Let's get started.")),
            result
        )
    }
}
