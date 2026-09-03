package me.kafuuneko.rpclient.libs.prompt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
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
    fun selectionUsesLogarithmicPrefixProbesForLargeHistory() {
        var probeCount = 0
        val messages = (1..100_000).toList()

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 54_321
        ) { prefix ->
            probeCount += 1
            prefix.size
        }

        assertEquals(54_321, selected.size)
        assertTrue(probeCount < 40)
    }

    @Test
    fun candidateLimitHonorsExplicitSettingAndPromptBudget() {
        assertEquals(900, summaryCandidateMessageLimit(1_000, 100, 0))
        assertEquals(50, summaryCandidateMessageLimit(1_000, 100, 50))
        assertEquals(900, summaryCandidateMessageLimit(1_000, 100, 2_000))
        assertEquals(256, nextSummaryCandidateWindowSize(128, 900))
        assertEquals(900, nextSummaryCandidateWindowSize(512, 900))
    }

    @Test
    fun boundedBpeCountingMatchesExactResultAndSignalsOverflow() {
        val tokenizer = PromptTokenizerRegistry().resolve(null)
        val messages = buildRawSummaryMessages(
            instruction = "Summarize the conversation",
            existingSummary = "Earlier events",
            history = (1..200).joinToString("\n") { "User: message-$it" }
        )
        val exactCount = tokenizer.countMessages(messages)

        assertEquals(exactCount, tokenizer.countMessagesUpTo(messages, exactCount))
        assertTrue(
            tokenizer.countMessagesUpTo(messages, exactCount - 1) > exactCount - 1
        )
    }

    @Test
    fun optimizedSelectionMatchesLegacyGreedyTokenBoundary() {
        val tokenizer = PromptTokenizerRegistry().resolve(null)
        val historyLines = (1..80).map { index ->
            when (index % 4) {
                0 -> "Alice: short message $index"
                1 -> "Character: 包含中文的历史消息 $index"
                2 -> "Alice: punctuation!? message-$index"
                else -> "Character: multiline $index\ncontinued"
            }
        }
        val budgets = listOf(32, 64, 128, 256, 512, 1_024)

        budgets.forEach { budget ->
            val exactSelection = historyLines.selectLinearly(budget) { prefix ->
                tokenizer.countMessages(summaryMessagesForHistory(prefix))
            }
            val optimizedSelection = selectSummaryPrefix(historyLines, budget) { prefix ->
                tokenizer.countMessagesUpTo(summaryMessagesForHistory(prefix), budget)
            }

            assertEquals(exactSelection, optimizedSelection)
        }
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

    /** 使用优化前的逐前缀算法生成兼容性基准。 */
    private fun <T> List<T>.selectLinearly(
        promptBudget: Int,
        countPrefixTokens: (List<T>) -> Int
    ): List<T> {
        val selected = mutableListOf<T>()
        for (item in this) {
            val candidate = selected + item
            if (countPrefixTokens(candidate) > promptBudget) break
            selected += item
        }
        return selected
    }

    /** 按生产摘要请求格式包装测试历史。 */
    private fun summaryMessagesForHistory(historyLines: List<String>) = buildRawSummaryMessages(
        instruction = "Summarize the conversation",
        existingSummary = "Earlier events",
        history = historyLines.joinToString("\n")
    )
}
