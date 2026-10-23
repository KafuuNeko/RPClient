package me.kafuuneko.rpclient.libs.story

import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer
import me.kafuuneko.rpclient.libs.prompt.WorldBookActivator
import me.kafuuneko.rpclient.libs.prompt.WorldBookGenerationType
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanContext
import me.kafuuneko.rpclient.libs.prompt.WorldBookScanMessage
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryContextSelectorTest {
    private val mSelector = StoryContextSelector()
    private val mTokenizer = object : PromptTokenizer {
        override val name = "test"
        override val strategy = PromptTokenizerStrategy.Conservative
        override val reservePercent = 0
        override fun countText(text: String) = text.length
        override fun countMessage(message: LLMMessage) = message.content.length
        override fun countMessages(messages: List<LLMMessage>) = messages.sumOf { it.content.length }
    }

    @Test
    fun continuationKeepsNearestEndingChunkRequired() {
        val content = "First paragraph.\n\nSecond paragraph.\n\nFinal paragraph."
        val result = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 2048,
        )

        assertTrue(result.chunks.last().required)
        assertTrue(result.chunks.last().content.contains("Final"))
    }

    @Test
    fun continuationGuidanceParticipatesInCharacterAndWorldBookScanning() {
        val content = "The empty station fell silent."
        val guidance = "Switch to Alice's point of view."

        val result = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 2048,
            continuationGuidance = guidance,
        )

        assertTrue(result.activationScanText.contains(guidance))
        assertTrue(result.worldBookScanText.contains(guidance))
    }

    @Test
    fun continuationGuidanceActivatesUnsetWholeWordCjkWorldInfo() {
        val content = "她站在门口。"
        val selection = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 2048,
            continuationGuidance = "接下来回学校。",
        )
        val entry = LorebookEntry(
            id = 1,
            lorebookId = 1,
            name = "School",
            keywords = """["学校"]""",
            secondaryKeywords = "[]",
            constant = false,
            order = 100,
            depth = 0,
            category = "[]",
            content = "School lore",
            matchWholeWords = null,
        )

        val activated = WorldBookActivator().activateStructured(
            context = WorldBookScanContext(
                messages = listOf(WorldBookScanMessage("", selection.worldBookScanText)),
                currentUserMessage = null,
                totalMessageCount = 1,
                worldInfoStateJson = "{}",
                candidateLorebookEntries = listOf(entry),
                generationType = WorldBookGenerationType.Continue,
                includeNames = false,
            ),
        )

        assertEquals(listOf(entry), activated.activatedEntries)
    }

    @Test
    fun longEmojiParagraphNeverSplitsUtf16SurrogatePair() {
        val content = "😀".repeat(400)
        val result = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 2048,
        )

        assertTrue(result.chunks.size > 1)
        result.chunks.forEach { chunk ->
            assertFalse(chunk.content.first().isLowSurrogate())
            assertFalse(chunk.content.last().isHighSurrogate())
        }
        assertTrue(result.chunks.joinToString("") { it.content } == content)
    }

    @Test
    fun oversizedFinalParagraphKeepsChunkNearestContinuationPoint() {
        val content = "BEGIN-" + "x".repeat(400) + "-TAIL"
        val result = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 128,
        )

        assertTrue(result.chunks.single().required)
        assertTrue(result.chunks.single().content.endsWith("-TAIL"))
        assertFalse(result.chunks.single().content.contains("BEGIN-"))
    }

    @Test
    fun hundredThousandCharacterDocumentBuildsBoundedPromptChunks() {
        val content = List(size = 8_000) { "第${it}段：雨落在旧城长街。" }.joinToString("\n\n")
        val result = mSelector.select(
            content = content,
            target = StoryEditTarget(content.length, content.length),
            authorNote = "",
            tokenizer = mTokenizer,
            promptBudget = 4096,
        )

        assertTrue(content.length > 100_000)
        assertTrue(result.chunks.isNotEmpty())
        assertTrue(result.chunks.size < 1_000)
        assertTrue(result.chunks.last().required)
        assertTrue(result.activationScanText.length < content.length)
    }
}
