package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LocalTokenEstimatorType
import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptRetentionPolicy
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.toConfig
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
    fun selectedWorldInfoIsNotDroppableWithHistory() {
        val result = finalize(
            drafts = listOf(
                draft("Required", priority = 1_000, canDrop = false),
                draft(
                    "H".repeat(30),
                    priority = PromptRetentionPolicy.HISTORY,
                    canDrop = true
                ),
                draft(
                    "W".repeat(30),
                    priority = 1_000,
                    canDrop = false,
                    sourceKind = PromptSourceKind.WorldInfo
                )
            ),
            contextTokens = 75,
            responseTokens = 10
        )

        assertFalse(result.request.messages.any { it.content == "H".repeat(30) })
        assertTrue(result.request.messages.any { it.content == "W".repeat(30) })
    }

    @Test
    fun selectedWorldInfoIsNotSilentlyTrimmedAfterBudgetSelection() {
        assertThrows(PromptBudgetExceededException::class.java) {
            finalize(
                drafts = listOf(
                    draft("Required", priority = 1_000, canDrop = false),
                    draft(
                        "W".repeat(80),
                        priority = 1_000,
                        canDrop = false,
                        sourceKind = PromptSourceKind.WorldInfo
                    )
                ),
                contextTokens = 50,
                responseTokens = 10
            )
        }
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
    fun longHistoryNeverOutranksUserNoteBecauseOfItsIndex() {
        val history = List(250) { index ->
            draft(
                content = "h$index",
                priority = PromptRetentionPolicy.HISTORY,
                canDrop = index != 249
            )
        }
        val result = finalize(
            drafts = listOf(
                draft("Required", priority = 1_000, canDrop = false),
                draft("USER_NOTE", priority = 300, canDrop = true)
            ) + history,
            contextTokens = 500,
            responseTokens = 50
        )

        assertTrue(result.request.messages.any { it.content == "USER_NOTE" })
        assertTrue(result.request.messages.any { it.content == "h249" })
        assertTrue(result.inspection.omittedItems.size > 200)
    }

    @Test
    fun noneModeCachesTokenCountsAndKeepsOriginalRemovalOrder() {
        val countingTokenizer = object : PromptTokenizer {
            override val name = "Counting tokenizer"
            override val strategy = PromptTokenizerStrategy.ModelAware
            override val supportsIncrementalMessageCounting = true
            var countTextCallCount = 0

            override fun countText(text: String): Int {
                countTextCallCount += 1
                return text.length
            }
        }
        val draftCount = 2_000
        val drafts = List(draftCount) { index ->
            PromptMessageDraft(
                role = LLMMessageRole.System,
                content = "x",
                source = PromptSource(PromptSourceKind.ChatHistory, "item-$index"),
                retentionPriority = PromptRetentionPolicy.HISTORY,
                canDrop = index != draftCount - 1
            )
        }

        val result = PromptRequestFinalizer { countingTokenizer }.finalize(
            drafts = drafts,
            provider = null,
            model = "test",
            options = LLMGenerationOptions(maxTokens = 10),
            includeReasoningInContent = false,
            maxContextTokens = 113,
            maxResponseTokens = 10,
            postProcessingMode = PromptPostProcessingMode.None,
            strictPromptPlaceholder = "[Start]"
        )

        assertEquals(10, result.request.messages.size)
        assertEquals("item-0", result.inspection.omittedItems.first().source.detail)
        assertEquals("item-1989", result.inspection.omittedItems.last().source.detail)
        assertEquals("item-1990", result.inspection.items.first().sources.single().detail)
        assertTrue(countingTokenizer.countTextCallCount <= draftCount * 3 + 2)
    }

    @Test
    fun noneModeIncrementalPathMatchesExactFallback() {
        val drafts = List(80) { index ->
            PromptMessageDraft(
                role = if (index % 2 == 0) {
                    LLMMessageRole.User
                } else {
                    LLMMessageRole.Assistant
                },
                content = if (index % 13 == 0) "" else "message-$index-${"x".repeat(index % 7)}",
                source = PromptSource(PromptSourceKind.ChatHistory, "item-$index"),
                retentionPriority = index % 5,
                canDrop = index != 79
            )
        }

        listOf(90, 220, 800).forEach { contextTokens ->
            val incremental = finalizeWithTokenizer(
                drafts = drafts,
                contextTokens = contextTokens,
                tokenizer = LengthPromptTokenizer(supportsIncrementalMessageCounting = true)
            )
            val exactFallback = finalizeWithTokenizer(
                drafts = drafts,
                contextTokens = contextTokens,
                tokenizer = LengthPromptTokenizer(supportsIncrementalMessageCounting = false)
            )

            assertEquals(exactFallback, incremental)
        }
    }

    @Test
    fun tokenizerRegistryUsesExactOpenAiAndModelFamilyProxies() {
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
        val claude = registry.resolve(
            provider(
                type = LLMProviderType.Claude,
                protocol = LLMProviderProtocol.AnthropicMessages,
                model = "claude-sonnet-4"
            )
        )
        val gemini = registry.resolve(
            provider(
                type = LLMProviderType.Gemini,
                protocol = LLMProviderProtocol.Gemini,
                model = "gemini-2.5-pro"
            )
        )
        val fallback = registry.resolve(
            provider(
                type = LLMProviderType.Custom,
                protocol = LLMProviderProtocol.OpenAICompatible,
                model = "unknown-local-model"
            )
        )

        assertEquals(PromptTokenizerStrategy.ModelAware, openAi.strategy)
        assertEquals(2, openAi.countText("hello world"))
        assertEquals(PromptTokenizerStrategy.Estimated, claude.strategy)
        assertEquals(PromptTokenizerStrategy.Estimated, gemini.strategy)
        assertEquals(PromptTokenizerStrategy.Estimated, fallback.strategy)
        assertEquals(15, claude.reservePercent)
        assertTrue(claude.name.contains("proxy"))
        assertTrue(gemini.name.contains("proxy"))
        assertTrue(fallback.countText("你好") in 1..5)
    }

    @Test
    fun manualTokenEstimatorOverridesAutomaticRulesForPromptAndUsage() {
        val registry = PromptTokenizerRegistry()
        val openAi = provider(
            type = LLMProviderType.ChatGPT,
            protocol = LLMProviderProtocol.OpenAICompatible,
            model = "gpt-4o-mini"
        ).copy(
            tokenEstimateReservePercent = 35,
            localTokenEstimatorType = LocalTokenEstimatorType.Cl100kBase
        )
        val unknown = provider(
            type = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            model = "unknown-model-alias"
        ).copy(
            tokenEstimateReservePercent = 35,
            localTokenEstimatorType = LocalTokenEstimatorType.O200kBase
        )

        // 手动类型不能被已知 OpenAI 模型或未知模型的自动回退覆盖。
        val promptCl100k = registry.resolve(openAi)
        val promptO200k = registry.resolve(unknown)
        assertEquals("CL100K proxy", promptCl100k.name)
        assertEquals("O200K proxy", promptO200k.name)
        assertEquals(PromptTokenizerStrategy.Estimated, promptCl100k.strategy)
        assertEquals(PromptTokenizerStrategy.Estimated, promptO200k.strategy)
        assertEquals(35, promptCl100k.reservePercent)
        assertEquals(35, promptO200k.reservePercent)

        // 用量统计复用同一编码选择，但不应用只属于 Prompt 预算的预留率。
        val usageCl100k = registry.resolveForUsage(openAi.toConfig())
        val usageO200k = registry.resolveForUsage(unknown.toConfig())
        assertEquals("CL100K proxy", usageCl100k.name)
        assertEquals("O200K proxy", usageO200k.name)
        assertEquals(0, usageCl100k.reservePercent)
        assertEquals(0, usageO200k.reservePercent)
    }

    @Test
    fun proxyReserveUsesTrueBudgetRatioAndIsProviderScoped() {
        val registry = PromptTokenizerRegistry()
        val baseProvider = provider(
            LLMProviderType.Claude,
            LLMProviderProtocol.AnthropicMessages,
            "claude"
        )
        val text = "one two three four five six seven eight nine ten"
        val baseTokens = registry.resolve(
            baseProvider.copy(tokenEstimateReservePercent = 0)
        ).countText(text)

        listOf(0, 15, 35, 50).forEach { reservePercent ->
            val tokenizer = registry.resolve(
                baseProvider.copy(tokenEstimateReservePercent = reservePercent)
            )
            val expected = kotlin.math.ceil(
                baseTokens * 100.0 / (100 - reservePercent)
            ).toInt()
            assertEquals(expected, tokenizer.countText(text))
            assertEquals(reservePercent, tokenizer.reservePercent)
        }
    }

    @Test
    fun proxyReserveCoercesPersistedValuesToSupportedRange() {
        val registry = PromptTokenizerRegistry()
        val baseProvider = provider(
            LLMProviderType.Claude,
            LLMProviderProtocol.AnthropicMessages,
            "claude"
        )

        assertEquals(
            0,
            registry.resolve(baseProvider.copy(tokenEstimateReservePercent = -1)).reservePercent
        )
        assertEquals(
            50,
            registry.resolve(baseProvider.copy(tokenEstimateReservePercent = 99)).reservePercent
        )
    }

    @Test
    fun promptInspectionRecordsProxyReserve() {
        val provider = provider(
            LLMProviderType.Claude,
            LLMProviderProtocol.AnthropicMessages,
            "claude"
        ).copy(tokenEstimateReservePercent = 35)
        val result = PromptRequestFinalizer(PromptTokenizerRegistry()).finalize(
            drafts = listOf(draft("Required", priority = 1_000, canDrop = false)),
            provider = provider,
            model = provider.model,
            options = LLMGenerationOptions(maxTokens = 10),
            includeReasoningInContent = false,
            maxContextTokens = 100,
            maxResponseTokens = 10,
            postProcessingMode = PromptPostProcessingMode.None,
            strictPromptPlaceholder = "[Start]"
        )

        assertEquals(PromptTokenizerStrategy.Estimated, result.inspection.tokenizerStrategy)
        assertEquals(35, result.inspection.tokenizerReservePercent)
    }

    @Test
    fun exactOpenAiTokenizerIgnoresProviderReserve() {
        val registry = PromptTokenizerRegistry()
        val openAi = provider(
            LLMProviderType.ChatGPT,
            LLMProviderProtocol.OpenAICompatible,
            "gpt-4o-mini"
        )
        val withoutReserve = registry.resolve(openAi.copy(tokenEstimateReservePercent = 0))
        val withReserve = registry.resolve(openAi.copy(tokenEstimateReservePercent = 50))

        assertEquals(0, withoutReserve.reservePercent)
        assertEquals(0, withReserve.reservePercent)
        assertEquals(withoutReserve.countText("hello world"), withReserve.countText("hello world"))
    }

    @Test
    fun unknownOpenAiModelUsesProviderReserveAsProxy() {
        val tokenizer = PromptTokenizerRegistry().resolve(
            provider(
                LLMProviderType.ChatGPT,
                LLMProviderProtocol.OpenAICompatible,
                "unknown-future-model"
            ).copy(tokenEstimateReservePercent = 35)
        )

        assertEquals(PromptTokenizerStrategy.Estimated, tokenizer.strategy)
        assertEquals(35, tokenizer.reservePercent)
    }

    @Test
    fun proxyTokenizersDoNotTreatEveryUtf8ByteAsAToken() {
        val registry = PromptTokenizerRegistry()
        val providers = listOf(
            provider(LLMProviderType.Claude, LLMProviderProtocol.AnthropicMessages, "claude"),
            provider(LLMProviderType.Gemini, LLMProviderProtocol.Gemini, "gemini"),
            provider(LLMProviderType.DeepSeek, LLMProviderProtocol.OpenAICompatible, "deepseek-chat"),
            provider(LLMProviderType.OpenRouter, LLMProviderProtocol.OpenAICompatible, "qwen/qwen3")
        )
        val text = "你好，这是一段用于上下文预算的消息。"
        val utf8Bytes = text.toByteArray(Charsets.UTF_8).size

        providers.forEach { provider ->
            val tokenizer = registry.resolve(provider)
            assertTrue(tokenizer.countText(text) < utf8Bytes)
            assertTrue(tokenizer.countText(text) > 0)
        }
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

    private fun finalizeWithTokenizer(
        drafts: List<PromptMessageDraft>,
        contextTokens: Int,
        tokenizer: PromptTokenizer
    ): PromptFinalizationResult {
        return PromptRequestFinalizer { tokenizer }.finalize(
            drafts = drafts,
            provider = null,
            model = "test",
            options = LLMGenerationOptions(maxTokens = 10),
            includeReasoningInContent = false,
            maxContextTokens = contextTokens,
            maxResponseTokens = 10,
            postProcessingMode = PromptPostProcessingMode.None,
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

    private fun provider(
        type: LLMProviderType,
        protocol: LLMProviderProtocol,
        model: String
    ): LLMProvider {
        return LLMProvider(
            name = "Test",
            providerType = type,
            protocol = protocol,
            baseUrl = "https://example.invalid",
            model = model
        )
    }

    /** 用于比较增量路径与精确回退路径的确定性 Tokenizer。 */
    private class LengthPromptTokenizer(
        override val supportsIncrementalMessageCounting: Boolean
    ) : PromptTokenizer {
        override val name = "Length tokenizer"
        override val strategy = PromptTokenizerStrategy.ModelAware

        override fun countText(text: String): Int = text.length
    }
}
