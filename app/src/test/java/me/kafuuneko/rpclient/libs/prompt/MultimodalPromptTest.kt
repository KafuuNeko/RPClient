package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.ImageInputCapabilityResolver
import me.kafuuneko.rpclient.libs.llm.ImageRequestException
import me.kafuuneko.rpclient.libs.llm.ImageRequestFailure
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
import me.kafuuneko.rpclient.libs.prompt.model.UnavailablePromptImage
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
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
    private fun image(id: String) = LLMImageReference(
        uuid = id,
        mimeType = "image/png",
        width = 640,
        height = 480,
        byteCount = 1000,
    )
    private fun draft(
        id: Long,
        text: String,
        count: Int,
        drop: Boolean,
    ) = PromptMessageDraft(
        role = LLMMessageRole.User,
        content = text,
        source = PromptSource(
            kind = PromptSourceKind.ChatHistory,
            referenceId = id,
        ),
        retentionPriority = id.toInt(),
        canDrop = drop,
        images = (0 until count).map { image("$id-$it") },
    )

    private fun finalize(
        drafts: List<PromptMessageDraft>,
        mode: PromptPostProcessingMode = PromptPostProcessingMode.None,
        budget: Int = 100_000,
    ) =
        PromptRequestFinalizer { tokenizer }.finalize(
            drafts = drafts,
            provider = null,
            model = "unknown",
            options = LLMGenerationOptions(),
            includeReasoningInContent = false,
            maxContextTokens = budget,
            maxResponseTokens = 1,
            postProcessingMode = mode,
            strictPromptPlaceholder = "start",
            postProcessingNames = PromptPostProcessingNames("User", "Character"),
        )

    @Test
    fun allPostProcessingModesRetainPureImagesAndInterleavedBoundaries() {
        PromptPostProcessingMode.entries.forEach { mode ->
            val result = finalize(
                drafts = listOf(
                    draft(id = 1, text = "first", count = 1, drop = true),
                    draft(
                        id = 2,
                        text = "",
                        count = 1,
                        drop = false,
                    ),
                ),
                mode = mode,
            )
            val blocks = result.request.messages.flatMap { message -> message.contentBlocks }
            assertEquals(
                listOf("1-0", "2-0"),
                result.request.messages.flatMap { message -> message.images }.map { image -> image.uuid },
            )
            val first = blocks.indexOfFirst { block -> block is LLMContentBlock.Image && block.reference.uuid == "1-0" }
            val second = blocks.indexOfFirst { block -> block is LLMContentBlock.Image && block.reference.uuid == "2-0" }
            assertTrue(blocks.subList(first + 1, second).any { block -> block is LLMContentBlock.Text && "first" in block.text })
            assertEquals(tokenizer.countMessages(result.request.messages), result.inspection.finalTokenCount)
        }
    }

    @Test
    fun imageCountDropsWholeOldMessagesAndNeverSplitsCurrentInput() {
        val result = finalize((1L..4L).map { draft(id = it, text = "text-$it", count = 4, drop = it != 4L) })
        assertEquals(12, result.request.messages.sumOf { it.images.size })
        assertFalse(result.request.messages.any { "text-1" in it.content })
        assertEquals(PromptOmissionReason.ImageCount, result.inspection.omittedItems.first().reason)
        assertThrows(IllegalStateException::class.java) { finalize(
            drafts = listOf(
                element = draft(
                    id = 1,
                text = "",
                count = 13,
                drop = false,
                ),
            ),
        ) }
    }

    @Test
    fun tokenBudgetIncludesImagesInFullAndBoundedCounting() {
        val messages = listOf(messageWithBlocks(LLMMessageRole.User, listOf(LLMContentBlock.Image(image("a")))))
        assertTrue(tokenizer.countMessages(messages) > 4096)
        assertEquals(
            tokenizer.countMessages(messages),
            tokenizer.countMessagesUpTo(
                messages = messages,
                maxTokens = 5000,
            ),
        )
        assertTrue(tokenizer.countMessagesUpTo(messages = messages, maxTokens = 100) > 100)
        assertThrows(PromptBudgetExceededException::class.java) { finalize(
            drafts = listOf(
                element = draft(
                    id = 1,
                    text = "",
                    count = 1,
                    drop = false,
                ),
            ),
            budget = 4096,
        ) }
    }

    @Test
    fun summaryPrefixCannotSkipOversizedFirstImageOrExceedImageCount() {
        val items = (1..4).toList()
        val selected = selectSummaryPrefix(items = items, promptBudget = 100_000) { prefix ->
            val blocks = prefix.flatMap { n -> (1..4).map { value -> LLMContentBlock.Image(image("$n-$value")) } }
            countSummaryTokens(
                tokenizer = tokenizer,
                messages = buildRawSummaryMessages("summary", "", "", blocks),
                budget = 100_000,
            )
        }
        assertEquals(listOf(1, 2, 3), selected)
        val none = selectSummaryPrefix(items = items, promptBudget = 100) { prefix ->
            countSummaryTokens(
                tokenizer = tokenizer,
                messages = buildRawSummaryMessages(
                    instruction = "summary",
                    existingSummary = "",
                    history = "",
                    historyBlocks = prefix.map { value -> LLMContentBlock.Image(image("$value")) },
                ),
                budget = 100,
            )
        }
        assertTrue(none.isEmpty())
    }

    @Test
    fun capabilityOverridesAndConfigurationChangesDoNotReuseStaleMetadata() {
        val resolver = ImageInputCapabilityResolver()
        val config = LLMProviderConfig(
            name = "test",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.invalid",
            model = "test",
        )
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config))
        resolver.record(config, listOf(LLMAvailableModel(id = "test", inputModalities = setOf("text"))))
        assertEquals(ImageInputSetting.Unsupported, resolver.resolve(config))
        assertEquals(
            ImageInputSetting.Supported,
            resolver.resolve(config.copy(imageInputSetting = ImageInputSetting.Supported)),
        )
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(baseUrl = "https://other.invalid")))
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(model = "other")))
        assertEquals(ImageInputSetting.Auto, resolver.resolve(config.copy(apiKey = "test-only")))
    }

    @Test
    fun logSanitizerRemovesShortImagesAndNestedErrorEchoes() {
        val secret = "YWJjZA=="
        listOf(
            """{"inlineData":{"mimeType":"image/png","data":"$secret"}}""",
            """{"source":{"type":"base64","data":"$secret"}}""",
            """{"url":"data:image/png;base64,$secret"}""",
            """{"error":"\"data\":\"$secret\""}""",
        ).forEach { input ->
            assertFalse(ImageLogSanitizer.sanitize(input).contains(secret))
        }
        assertTrue(ImageLogSanitizer.sanitize("x ".repeat(100_000)).length < 66_000)
    }
    @Test
    fun unavailableOldImagesAreDroppedAtomicallyInEveryPostProcessingMode() {
        val missing = draft(id = 1, text = "", count = 0, drop = true).copy(
            unavailableImages = listOf(
                element = UnavailablePromptImage(
                    messageId = 1,
                    uuid = "missing",
                    position = 0,
                    failure = ImageRequestFailure.Missing,
                ),
            ),
        )
        for (mode in PromptPostProcessingMode.entries) {
            val result = finalize(
                drafts = listOf(missing, draft(id = 2, text = "latest", count = 0, drop = false)),
                mode = mode,
                budget = 200,
            )
            assertEquals(listOf(1L), result.inspection.omittedItems.map { it.source.referenceId })
            assertEquals(4096, result.inspection.omittedItems.single().tokenCount)
            assertTrue(result.request.messages.all { it.images.isEmpty() })
            val failure = assertThrows(ImageRequestException::class.java) {
                finalize(listOf(missing, draft(id = 2, text = "latest", count = 0, drop = false)), mode)
            }
            assertEquals(ImageRequestFailure.Missing, failure.failure)
        }
    }

    @Test
    fun protectedMissingImageIsNeverSilentlyRemoved() {
        val missing = draft(id = 2, text = "", count = 0, drop = false).copy(
            unavailableImages = listOf(
                element = UnavailablePromptImage(
                    messageId = 2,
                    uuid = "missing",
                    position = 0,
                    failure = ImageRequestFailure.InvalidImage,
                ),
            ),
        )
        val failure = assertThrows(ImageRequestException::class.java) { finalize(listOf(missing)) }
        assertEquals(ImageRequestFailure.InvalidImage, failure.failure)
        assertThrows(PromptBudgetExceededException::class.java) { finalize(drafts = listOf(missing), budget = 100) }
    }

    @Test
    fun summaryOnlyCountsMissingAttachmentsInItsSelectedPrefix() {
        val items = listOf(1, 2, 3)
        val selected = selectSummaryPrefix(items = items, promptBudget = 100) { prefix ->
            countSummaryTokens(
                tokenizer = tokenizer,
                messages = buildRawSummaryMessages("summarize", "", "ok"),
                budget = 100,
                unavailableCount = if (2 in prefix) 1 else 0,
            )
        }
        assertEquals(listOf(1), selected)
    }

    @Test
    fun finalizerUsesActualModelAndUnsupportedOmissionsHaveNoImageCost() {
        val provider = LLMProvider(
            name = "test",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.invalid",
            model = "gpt-4o",
        )
        val finalizer = PromptRequestFinalizer()
        val current = draft(id = 2, text = "latest", count = 1, drop = false).copy(
            images = listOf(
                element = image("a").copy(
                    width = 1024,
                height = 1024,
                ),
            ),
        )
        val actual = finalizer.finalize(
            drafts = listOf(current),
            provider = provider,
            model = "gpt-4o-mini",
            options = LLMGenerationOptions(),
            includeReasoningInContent = false,
            maxContextTokens = 100_000,
            maxResponseTokens = 1,
            postProcessingMode = PromptPostProcessingMode.None,
            strictPromptPlaceholder = "start",
        )
        assertEquals(listOf(25501L), actual.inspection.items.single().imageTokenCounts)
        val unsupported = provider.copy(imageInputSetting = ImageInputSetting.Unsupported)
        val old = draft(id = 1, text = "word ".repeat(100), count = 1, drop = true)
        val counted = finalizer.finalize(
            drafts = listOf(old, current),
            provider = unsupported,
            model = unsupported.model,
            options = LLMGenerationOptions(),
            includeReasoningInContent = false,
            maxContextTokens = 40,
            maxResponseTokens = 1,
            postProcessingMode = PromptPostProcessingMode.None,
            strictPromptPlaceholder = "start",
        )
        assertEquals(listOf(0L), counted.inspection.items.single().imageTokenCounts)
        assertEquals(
            PromptTokenizerRegistry().resolve(unsupported).countText(old.content),
            counted.inspection.omittedItems.single().tokenCount,
        )
    }

}
