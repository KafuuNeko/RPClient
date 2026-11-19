package me.kafuuneko.rpclient.libs.prompt.summary

import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.SummaryInjectionPosition
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPromptBuilderTest {
    @Test
    fun classicTaskKeepsHistoryUnderContextBudgetAndExpandsWordLimit() {
        val instruction = classicSummaryInstruction(
            template = "Summarize {{char}} in {{ WORDS }} words. {{ HISTORY }} {{Summary}}",
            wordsLimit = 500,
        )
        assertEquals("Summarize {{char}} in 500 words.  ", instruction)
        assertThrows(IllegalArgumentException::class.java) {
            classicSummaryInstruction("{{history}} {{summary}}", 500)
        }
    }

    @Test
    fun selectionCountsTemplateAndFormattingOverheadForEveryPrefix() {
        val messages = listOf("one", "two", "three")

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 18,
        ) { prefix ->
            10 + prefix.sumOf { text -> text.length + 1 }
        }

        assertEquals(listOf("one", "two"), selected)
    }

    @Test
    fun selectionRejectsFirstMessageWhenCompleteRequestExceedsBudget() {
        val selected = selectSummaryPrefix(
            items = listOf("oversized"),
            promptBudget = 8,
        ) { 9 }

        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun selectionUsesLogarithmicPrefixProbesForLargeHistory() {
        var probeCount = 0
        val messages = (1..100_000).toList()

        val selected = selectSummaryPrefix(
            items = messages,
            promptBudget = 54_321,
        ) { prefix ->
            probeCount += 1
            prefix.size
        }

        assertEquals(54_321, selected.size)
        assertTrue(probeCount < 40)
    }

    @Test
    fun candidateLimitHonorsExplicitSettingAndPromptBudget() {
        assertEquals(
            900,
            summaryCandidateMessageLimit(
                maxContextTokens = 1_000,
                responseTokens = 100,
                configuredMaxMessages = 0,
            ),
        )
        assertEquals(
            50,
            summaryCandidateMessageLimit(
                maxContextTokens = 1_000,
                responseTokens = 100,
                configuredMaxMessages = 50,
            ),
        )
        assertEquals(
            900,
            summaryCandidateMessageLimit(
                maxContextTokens = 1_000,
                responseTokens = 100,
                configuredMaxMessages = 2_000,
            ),
        )
        assertEquals(256, nextSummaryCandidateWindowSize(currentSize = 128, maximumSize = 900))
        assertEquals(900, nextSummaryCandidateWindowSize(currentSize = 512, maximumSize = 900))
    }

    @Test
    fun boundedBpeCountingMatchesExactResultAndSignalsOverflow() {
        val tokenizer = PromptTokenizerRegistry().resolve(provider = null)
        val history = (1..200).map { index ->
            RawSummaryHistoryMessage(speakerName = "User", content = "message-$index")
        }
        val messages = summaryMessagesForHistory(history)
        val exactCount = tokenizer.countMessages(messages)

        assertEquals(exactCount, tokenizer.countMessagesUpTo(messages, exactCount))
        assertTrue(tokenizer.countMessagesUpTo(messages, exactCount - 1) > exactCount - 1)
    }

    @Test
    fun optimizedSelectionMatchesLegacyGreedyTokenBoundary() {
        val tokenizer = PromptTokenizerRegistry().resolve(provider = null)
        val history = (1..80).map { index ->
            val content = when (index % 4) {
                0 -> "short message $index"
                1 -> "包含中文的历史消息 $index"
                2 -> "punctuation!? message-$index"
                else -> "multiline $index\ncontinued"
            }
            val speakerName = if (index % 2 == 0) "Alice" else "Character"
            RawSummaryHistoryMessage(speakerName = speakerName, content = content)
        }
        val budgets = listOf(32, 64, 128, 256, 512, 1_024)

        budgets.forEach { budget ->
            val exactSelection = history.selectLinearly(budget) { prefix ->
                tokenizer.countMessages(summaryMessagesForHistory(prefix))
            }
            val optimizedSelection = selectSummaryPrefix(history, budget) { prefix ->
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
            SummaryInjectionPosition.default,
        )
        assertEquals(
            SummaryInjectionPosition.None,
            SummaryInjectionPosition.fromPersistedValue(-1),
        )
        assertEquals(
            SummaryInjectionPosition.BeforeMain,
            SummaryInjectionPosition.fromPersistedValue(0),
        )
        assertEquals(
            SummaryInjectionPosition.AfterMain,
            SummaryInjectionPosition.fromPersistedValue(1),
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(2),
        )
        assertEquals(
            SummaryInjectionPosition.InChat,
            SummaryInjectionPosition.fromPersistedValue(3),
        )
        assertEquals(
            SummaryInjectionPosition.default,
            SummaryInjectionPosition.fromPersistedValue(Int.MAX_VALUE),
        )
    }

    @Test
    fun rawSummaryUsesSystemInstructionAndUserMaterial() {
        val messages = buildRawSummaryMessages(
            templates = PromptTemplateSnapshot.defaults(),
            instruction = "Summarize",
            existingSummary = "Earlier events",
            history = listOf(RawSummaryHistoryMessage(speakerName = "User", content = "New event")),
        )

        assertEquals(
            listOf(LLMMessageRole.System, LLMMessageRole.User),
            messages.map { it.role },
        )
        assertEquals("Summarize", messages[0].content)
        assertEquals(
            "<history_summary>\nEarlier events\n</history_summary>\n\n" +
                "<chat_history>\nUser: New event\n</chat_history>",
            messages[1].content,
        )
    }

    @Test
    fun rawSummaryWrapsImagesInPlaceAndRespectsAnEmptyImageInstruction() {
        val templates = PromptTemplateSnapshot.resolve(
            presetId = 42,
            revision = 1,
            overrides = mapOf(
                PromptType.RawSummaryPreviousFormat.key to "<prior>{{summary}}</prior>",
                PromptType.RawSummaryHistoryFormat.key to "<recent>\n{{ HISTORY }}\n</recent>",
                PromptType.RawSummaryImagePrompt.key to "Summarize the visible objects.",
            ),
        )
        val image = LLMImageReference(
            uuid = "image",
            mimeType = "image/png",
            width = 64,
            height = 64,
            byteCount = 512,
        )
        val history = listOf(
            RawSummaryHistoryMessage(speakerName = "User", content = "{{history}}"),
            RawSummaryHistoryMessage(speakerName = "Lina", content = "", images = listOf(image)),
        )
        // 验证宏样式的素材保持原文，闭合标签跟在没有后续文字的图片之后。
        val messages = buildRawSummaryMessages(
            templates = templates,
            instruction = "Summarize",
            existingSummary = "Past events",
            history = history,
        )
        assertEquals("Summarize\nSummarize the visible objects.", messages[0].content)
        assertEquals(
            listOf(
                LLMContentBlock.Text("<prior>Past events</prior>"),
                LLMContentBlock.Text("<recent>\nUser: {{history}}\nLina:\n"),
                LLMContentBlock.Image(image),
                LLMContentBlock.Text("\n</recent>"),
            ),
            messages[1].contentBlocks,
        )

        // 显式空值关闭指令，图片及其历史包装仍需发送。
        val withoutImageInstruction = templates.copy(
            values = templates.values + (PromptType.RawSummaryImagePrompt to ""),
        )
        val firstSummary = buildRawSummaryMessages(
            templates = withoutImageInstruction,
            instruction = "Summarize",
            existingSummary = "",
            history = history,
        )
        assertEquals("Summarize", firstSummary[0].content)
        assertEquals(messages[1].contentBlocks.drop(1), firstSummary[1].contentBlocks)
    }

    @Test
    fun rawSummaryMessageFormatWrapsEachSpeakersTextAndImages() {
        val templates = PromptTemplateSnapshot.resolve(
            presetId = 42,
            revision = 1,
            overrides = mapOf(
                PromptType.RawSummaryMessageFormat.key to
                    "<message>\n<speaker>{{speaker}}</speaker>\n{{ MESSAGE }}\n</message>",
            ),
        )
        val photograph = LLMImageReference(
            uuid = "photo",
            mimeType = "image/png",
            width = 64,
            height = 64,
            byteCount = 512,
        )
        val sketch = photograph.copy(uuid = "sketch")
        // 两位发言者的原始署名和正文含有类似宏的文字，只有预设里的宏应展开。
        val history = listOf(
            RawSummaryHistoryMessage(
                speakerName = "Lina {{message}}",
                content = "A new line\n{{speaker}}",
                images = listOf(photograph),
            ),
            RawSummaryHistoryMessage(speakerName = "Kai", content = "", images = listOf(sketch)),
        )
        val messages = buildRawSummaryMessages(
            templates = templates,
            instruction = "Summarize",
            existingSummary = "",
            history = history,
        )
        // 一组历史标签包住所有消息，每条消息的后缀都留在内容和所属图片之后。
        assertEquals(
            listOf(
                LLMContentBlock.Text("<chat_history>\n<message>\n<speaker>Lina {{message}}</speaker>\n"),
                LLMContentBlock.Image(photograph),
                LLMContentBlock.Text("A new line\n{{speaker}}\n</message>\n<message>\n<speaker>Kai</speaker>\n"),
                LLMContentBlock.Image(sketch),
                LLMContentBlock.Text("\n</message>\n</chat_history>"),
            ),
            messages.last().contentBlocks,
        )
    }

    @Test
    fun malformedRawSummaryFormatsCannotOmitOrDuplicateMaterial() {
        val previousFormat = "<prior></prior>"
        val historyFormat = "{{history}}{{ HISTORY }}"
        val messageFormat = "{{speaker}}: {{message}}{{ MESSAGE }}"
        assertEquals(
            null,
            PromptType.RawSummaryPreviousFormat.splitRawSummaryFormat(previousFormat)
        )
        assertEquals(null, PromptType.RawSummaryHistoryFormat.splitRawSummaryFormat(historyFormat))
        assertEquals(null, PromptType.RawSummaryMessageFormat.splitRawSummaryFormat(messageFormat))
        val templates = PromptTemplateSnapshot.resolve(
            presetId = 42,
            revision = 1,
            overrides = mapOf(
                PromptType.RawSummaryPreviousFormat.key to previousFormat,
                PromptType.RawSummaryHistoryFormat.key to historyFormat,
                PromptType.RawSummaryMessageFormat.key to messageFormat,
            ),
        )
        // 即使覆盖值绕过编辑器进入快照，默认包装也必须完整保留素材。
        val history = listOf(RawSummaryHistoryMessage(speakerName = "User", content = "New event"))
        val messages = buildRawSummaryMessages(
            templates = templates,
            instruction = "Summarize",
            existingSummary = "Past events",
            history = history,
        )
        val defaults = buildRawSummaryMessages(
            templates = PromptTemplateSnapshot.defaults(),
            instruction = "Summarize",
            existingSummary = "Past events",
            history = history,
        )
        assertEquals(defaults, messages)
    }

    @Test
    fun summaryCandidatesAlwaysExcludeLastMessage() {
        assertEquals(
            listOf("one", "two"),
            listOf("one", "two", "three").summaryCandidates(maxMessages = 0)
        )
        assertEquals(
            listOf("one"),
            listOf("one", "two", "three").summaryCandidates(maxMessages = 1)
        )
    }

    /** 使用优化前的逐前缀算法生成兼容性基准。 */
    private fun <T> List<T>.selectLinearly(
        promptBudget: Int,
        countPrefixTokens: (List<T>) -> Int,
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
    private fun summaryMessagesForHistory(history: List<RawSummaryHistoryMessage>) =
        buildRawSummaryMessages(
            templates = PromptTemplateSnapshot.defaults(),
            instruction = "Summarize the conversation",
            existingSummary = "Earlier events",
            history = history,
        )
}
