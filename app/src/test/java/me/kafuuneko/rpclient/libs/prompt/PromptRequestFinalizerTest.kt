package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptRequestFinalizerTest {
    private val tokenizer = object : PromptTokenizer {
        override val name = "Test tokenizer"
        override val strategy = PromptTokenizerStrategy.ModelAware

        override fun countText(text: String): Int = text.length
    }
    private val finalizer = PromptRequestFinalizer { tokenizer }

    @Test
    fun removesLowestPriorityWholeItemUntilFinalRequestFits() {
        val result = finalize(
            drafts = listOf(
                draft("Required", priority = 1_000, canDrop = false),
                draft("x".repeat(40), priority = 10, canDrop = true),
                draft("Recent", priority = 100, canDrop = true)
            ),
            contextTokens = 55,
            responseTokens = 10
        )

        assertTrue(result.inspection.finalTokenCount <= result.inspection.promptBudget)
        assertFalse(result.request.messages.any { it.content == "x".repeat(40) })
        assertEquals(
            PromptOmissionReason.ContextBudget,
            result.inspection.omittedItems.first().reason
        )
    }

    @Test
    fun countsSingleUserPostProcessingBeforeApplyingBudget() {
        val result = finalize(
            drafts = listOf(
                draft("Required", priority = 1_000, canDrop = false),
                draft("Optional text", priority = 10, canDrop = true)
            ),
            contextTokens = 35,
            responseTokens = 10,
            mode = PromptPostProcessingMode.SingleUserMessage
        )

        assertEquals(1, result.request.messages.size)
        assertTrue(result.inspection.finalTokenCount <= 40)
        assertTrue(result.inspection.omittedItems.isNotEmpty())
    }

    @Test
    fun refusesToSilentlyTruncateRequiredContent() {
        assertThrows(PromptBudgetExceededException::class.java) {
            finalize(
                drafts = listOf(
                    draft("x".repeat(80), priority = 1_000, canDrop = false)
                ),
                contextTokens = 50,
                responseTokens = 10
            )
        }
    }

    @Test
    fun marksPrefixCacheSensitiveSources() {
        val result = finalize(
            drafts = listOf(
                draft(
                    "Lore",
                    priority = 1_000,
                    canDrop = false,
                    sourceKind = PromptSourceKind.WorldInfo
                ),
                draft(
                    "Memory",
                    priority = 1_000,
                    canDrop = false,
                    sourceKind = PromptSourceKind.Summary
                ),
                draft(
                    "Hello",
                    priority = 1_000,
                    canDrop = false,
                    sourceKind = PromptSourceKind.ChatHistory
                )
            ),
            contextTokens = 80,
            responseTokens = 10
        )

        val noteKinds = result.inspection.items.flatMap { item ->
            item.cacheNotes.map { it.kind }
        }

        assertTrue(PromptCacheNoteKind.PrefixWorldInfo in noteKinds)
        assertTrue(PromptCacheNoteKind.PrefixSummary in noteKinds)
    }

    @Test
    fun marksHistoryTrimAsCacheBoundaryChange() {
        val result = finalize(
            drafts = listOf(
                draft("Required", priority = 1_000, canDrop = false),
                draft(
                    "x".repeat(40),
                    priority = 10,
                    canDrop = true,
                    sourceKind = PromptSourceKind.ChatHistory
                ),
                draft(
                    "Recent",
                    priority = 1_000,
                    canDrop = false,
                    sourceKind = PromptSourceKind.ChatHistory
                )
            ),
            contextTokens = 55,
            responseTokens = 10
        )

        assertTrue(
            result.inspection.cacheNotes.any {
                it.kind == PromptCacheNoteKind.ContextTrim
            }
        )
    }

    @Test
    fun tokenizerRegistryUsesBpeForOpenAiAndUtf8UpperBoundForUnknownModels() {
        val registry = PromptTokenizerRegistry()
        val openAi = registry.resolve(
            LLMProvider(
                name = "OpenAI",
                providerType = LLMProviderType.ChatGPT,
                protocol = LLMProviderProtocol.OpenAICompatible,
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini"
            )
        )
        val fallback = registry.resolve(null)

        assertEquals(PromptTokenizerStrategy.ModelAware, openAi.strategy)
        assertEquals(2, openAi.countText("hello world"))
        assertEquals(PromptTokenizerStrategy.Conservative, fallback.strategy)
        assertEquals(6, fallback.countText("你好"))
    }

    private fun finalize(
        drafts: List<PromptMessageDraft>,
        contextTokens: Int,
        responseTokens: Int,
        mode: PromptPostProcessingMode = PromptPostProcessingMode.None
    ): PromptFinalizationResult {
        return finalizer.finalize(
            drafts = drafts,
            provider = null,
            model = "test",
            options = LLMGenerationOptions(maxTokens = responseTokens),
            includeReasoningInContent = false,
            maxContextTokens = contextTokens,
            maxResponseTokens = responseTokens,
            postProcessingMode = mode,
            strictPromptPlaceholder = "[Start]"
        )
    }

    private fun draft(
        content: String,
        priority: Int,
        canDrop: Boolean,
        sourceKind: PromptSourceKind = PromptSourceKind.Other
    ): PromptMessageDraft {
        return PromptMessageDraft(
            role = LLMMessageRole.System,
            content = content,
            source = PromptSource(sourceKind, content.take(8)),
            retentionPriority = priority,
            canDrop = canDrop
        )
    }
}
