package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.adapter.ImageLogSanitizer
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.ImageInputSetting
import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保护图文顺序、原子预算和摘要前缀这些跨模块易碎语义。 */
class MultimodalPromptTest {
    private val tokenizer = object : PromptTokenizer {
        override val name = "test"
        override val strategy = PromptTokenizerStrategy.Estimated
        override fun countText(text: String) = text.length
    }
    private fun image(id: String) = LLMImageReference(id, id, "image/png", 640, 480, 1000)
    private fun draft(id: Long, text: String, count: Int, drop: Boolean) = PromptMessageDraft(
        LLMMessageRole.User, text, PromptSource(PromptSourceKind.ChatHistory, referenceId = id),
        retentionPriority = id.toInt(), canDrop = drop, images = (0 until count).map { image("$id-$it") })

    private fun finalize(drafts: List<PromptMessageDraft>, mode: PromptPostProcessingMode = PromptPostProcessingMode.None, budget: Int = 100_000) =
        PromptRequestFinalizer { tokenizer }.finalize(drafts, null, "unknown", LLMGenerationOptions(), false,
            maxContextTokens = budget, maxResponseTokens = 1, postProcessingMode = mode,
            strictPromptPlaceholder = "start", postProcessingNames = PromptPostProcessingNames("User", "Character"))

    @Test
    fun allPostProcessingModesRetainPureImagesAndInterleavedBoundaries() {
        PromptPostProcessingMode.entries.forEach { mode ->
            val result = finalize(listOf(draft(1, "first", 1, true), draft(2, "", 1, false)), mode)
            val blocks = result.request.messages.flatMap { it.contentBlocks }
            assertEquals(listOf("1-0", "2-0"), result.request.messages.flatMap { it.images }.map { it.uuid })
            val first = blocks.indexOfFirst { it is LLMContentBlock.Image && it.reference.uuid == "1-0" }
            val second = blocks.indexOfFirst { it is LLMContentBlock.Image && it.reference.uuid == "2-0" }
            assertTrue(blocks.subList(first + 1, second).any { it is LLMContentBlock.Text && "first" in it.text })
            assertEquals(tokenizer.countMessages(result.request.messages), result.inspection.finalTokenCount)
        }
    }

    @Test
    fun imageCountDropsWholeOldMessagesAndNeverSplitsCurrentInput() {
        val result = finalize((1L..4L).map { draft(it, "text-$it", 4, it != 4L) })
        assertEquals(12, result.request.messages.sumOf { it.images.size })
        assertFalse(result.request.messages.any { "text-1" in it.content })
        assertEquals(PromptOmissionReason.ImageCount, result.inspection.omittedItems.first().reason)
        assertThrows(IllegalStateException::class.java) { finalize(listOf(draft(1, "", 13, false))) }
    }

    @Test
    fun tokenBudgetIncludesImagesInFullAndBoundedCounting() {
        val messages = listOf(messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(image("a")))))
        assertTrue(tokenizer.countMessages(messages) > 4096)
        assertEquals(tokenizer.countMessages(messages), tokenizer.countMessagesUpTo(messages, 5000))
        assertTrue(tokenizer.countMessagesUpTo(messages, 100) > 100)
        assertThrows(PromptBudgetExceededException::class.java) { finalize(listOf(draft(1, "", 1, false)), budget = 4096) }
    }

    @Test
    fun summaryPrefixCannotSkipOversizedFirstImageOrExceedImageCount() {
        val items = (1..4).toList()
        val selected = selectSummaryPrefix(items, 100_000) { prefix ->
            val blocks = prefix.flatMap { n -> (1..4).map { LLMContentBlock.Image(image("$n-$it")) } }
            countSummaryTokens(tokenizer, buildRawSummaryMessages("summary", "", "", blocks), 100_000)
        }
        assertEquals(listOf(1, 2, 3), selected)
        val none = selectSummaryPrefix(items, 100) { prefix ->
            countSummaryTokens(tokenizer, buildRawSummaryMessages("summary", "", "",
                prefix.map { LLMContentBlock.Image(image("$it")) }), 100)
        }
        assertTrue(none.isEmpty())
    }

    @Test
    fun capabilityOverridesAndConfigurationChangesDoNotReuseStaleMetadata() {
        val resolver = ImageInputCapabilityResolver()
        val config = LLMProviderConfig("test", LLMProviderType.Custom, LLMProviderProtocol.OpenAICompatible,
            "https://example.invalid", model = "test")
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config))
        resolver.record(config, listOf(LLMAvailableModel("test", inputModalities = setOf("text"))))
        assertEquals(ImageInputSetting.Unsupported, resolver.resolve(config))
        assertEquals(ImageInputSetting.Supported, resolver.resolve(config.copy(imageInputSetting = ImageInputSetting.Supported)))
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(baseUrl = "https://other.invalid")))
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(model = "other")))
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(apiKey = "test-only")))
    }

    @Test
    fun logSanitizerRemovesShortImagesAndNestedErrorEchoes() {
        val secret = "YWJjZA=="
        listOf("""{"inlineData":{"mimeType":"image/png","data":"$secret"}}""",
            """{"source":{"type":"base64","data":"$secret"}}""",
            """{"url":"data:image/png;base64,$secret"}""",
            """{"error":"\"data\":\"$secret\""}""").forEach { input ->
            assertFalse(ImageLogSanitizer.sanitize(input).contains(secret))
        }
        assertTrue(ImageLogSanitizer.sanitize("x ".repeat(100_000)).length < 66_000)
    }
}
