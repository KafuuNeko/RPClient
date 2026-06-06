package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPromptBuilderTest {
    @Test
    fun selectionStopsAtFirstMessageThatExceedsBudget() {
        val messages = listOf(
            message(id = 1L, content = "a".repeat(3)),
            message(id = 2L, content = "b".repeat(3_600)),
            message(id = 3L, content = "c".repeat(3))
        )

        val selected = selectSummaryMessagePrefix(
            messages = messages,
            maxMessages = 0,
            tokenBudget = 1_024
        )

        assertEquals(listOf(1L), selected.map { it.id })
    }

    private fun message(id: Long, content: String): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = 1L,
            createTime = id,
            source = ChatMessage.Source.User,
            content = content
        )
    }
}
