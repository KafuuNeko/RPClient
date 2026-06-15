package me.kafuuneko.rpclient.libs.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import org.junit.Test

class SummaryPromptBuilderTest {
    @Test
    fun selectionCountsTemplateAndFormattingOverheadForEveryPrefix() {
        val messages = listOf("one", "two", "three")

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 18
        ) { prefix ->
            10 + prefix.sumOf { it.length + 1 }
        }

        assertEquals(listOf("one", "two"), selected)
    }

    @Test
    fun selectionRejectsFirstMessageWhenCompleteRequestExceedsBudget() {
        val selected = selectSummaryPrefix(
            items = listOf("oversized"),
            promptBudget = 8
        ) { 9 }

        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun summaryContentAlwaysRemovesReasoningBlocks() {
        val content = "<think>private chain</think>\nVisible event"
            .summarySafeContent()

        assertEquals("Visible event", content)
        assertFalse(content.contains("private chain"))
    }

    @Test
    fun legacySummaryPositionsMigrateToCurrentSemantics() {
        assertEquals(
            SummaryInjectionPosition.AfterMain,
            SummaryInjectionPosition.default
        )
        assertEquals(
            SummaryInjectionPosition.None,
            SummaryInjectionPosition.fromPersistedValue(-1)
        )
        assertEquals(
            SummaryInjectionPosition.BeforeMain,
            SummaryInjectionPosition.fromPersistedValue(0)
        )
        assertEquals(
            SummaryInjectionPosition.AfterMain,
            SummaryInjectionPosition.fromPersistedValue(1)
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(2)
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(3)
        )
        assertEquals(
            SummaryInjectionPosition.default,
            SummaryInjectionPosition.fromPersistedValue(Int.MAX_VALUE)
        )
    }

    @Test
    fun rawSummaryUsesSystemInstructionAndUserMaterial() {
        val messages = buildRawSummaryMessages(
            instruction = "Summarize",
            existingSummary = "Earlier events",
            history = "User: New event"
        )

        assertEquals(
            listOf(LLMMessageRole.System, LLMMessageRole.User),
            messages.map { it.role }
        )
        assertEquals("Summarize", messages[0].content)
        assertEquals(
            "Existing summary:\nEarlier events\n\nChat history:\nUser: New event",
            messages[1].content
        )
    }

    @Test
    fun summaryCandidatesAlwaysExcludeLastMessage() {
        assertEquals(listOf("one", "two"), listOf("one", "two", "three").summaryCandidates(0))
        assertEquals(listOf("one"), listOf("one", "two", "three").summaryCandidates(1))
    }
}
