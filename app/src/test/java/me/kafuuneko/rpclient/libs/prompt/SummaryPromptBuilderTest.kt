package me.kafuuneko.rpclient.libs.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
