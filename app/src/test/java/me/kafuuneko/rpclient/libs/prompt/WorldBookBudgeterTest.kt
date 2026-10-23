package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspectionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBookBudgeterTest {
    private val tokenizer = object : PromptTokenizer {
        override val name: String = "Length"
        override val strategy: PromptTokenizerStrategy = PromptTokenizerStrategy.ModelAware

        override fun countText(text: String): Int = text.length
    }

    @Test
    fun globalBudgetAppliesCapAfterContextPercentage() {
        assertEquals(
            1_200,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 25, tokenBudgetCap = 1_200),
        )
        assertEquals(
            2_500,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 25, tokenBudgetCap = 0),
        )
        assertEquals(
            2_500,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 25, tokenBudgetCap = 4_000),
        )
    }

    @Test
    fun globalBudgetMatchesSillyTavernRoundingAndMinimum() {
        assertEquals(
            1,
            resolveWorldInfoBudget(promptTokenBudget = 1, contextPercent = 50, tokenBudgetCap = 0),
        )
        assertEquals(
            1,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 0, tokenBudgetCap = 0),
        )
        assertEquals(
            2,
            resolveWorldInfoBudget(promptTokenBudget = 5, contextPercent = 30, tokenBudgetCap = 0),
        )
    }

    @Test
    fun globalBudgetNormalizesPersistedSettings() {
        // 百分比归一化后，即使输入预算达到 Int 上限，结果也不会溢出。
        assertEquals(
            Int.MAX_VALUE,
            resolveWorldInfoBudget(
                promptTokenBudget = Int.MAX_VALUE,
                contextPercent = Int.MAX_VALUE,
                tokenBudgetCap = 0,
            ),
        )
        assertEquals(
            10_000,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 120, tokenBudgetCap = 0),
        )
        assertEquals(
            1,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = -5, tokenBudgetCap = 0),
        )
        assertEquals(
            2_500,
            resolveWorldInfoBudget(promptTokenBudget = 10_000, contextPercent = 25, tokenBudgetCap = 12_000),
        )
        assertEquals(
            1,
            resolveWorldInfoBudget(promptTokenBudget = -1, contextPercent = 25, tokenBudgetCap = -1),
        )
    }

    @Test
    fun entryExactlyReachingGlobalBudgetIsOmittedLikeSillyTavern() {
        val entry = entry(id = 1L, content = "1234567890", order = 10)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(entry)),
            globalTokenBudget = 10,
            lorebooks = emptyMap(),
            tokenizer = tokenizer,
        )

        assertTrue(selection.result.activatedEntries.isEmpty())
        assertEquals(PromptOmissionReason.WorldInfoBudget, selection.omittedItems.single().reason)
    }

    @Test
    fun constantEntriesAreBudgetedBeforeHigherOrderNormalEntries() {
        val constant = entry(id = 1L, content = "CCCC", constant = true, order = 1)
        val normal = entry(id = 2L, content = "NNNN", constant = false, order = 100)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(normal, constant)),
            globalTokenBudget = 6,
            lorebooks = emptyMap(),
            tokenizer = tokenizer,
        )

        assertEquals(listOf(constant), selection.result.activatedEntries)
        assertTrue(selection.omittedItems.any { it.source.detail == normal.name })
    }

    @Test
    fun lorebookTokenBudgetLimitsEntriesFromThatBook() {
        val first = entry(id = 1L, content = "12345678", order = 20)
        val second = entry(id = 2L, content = "abcdefgh", order = 10)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(first, second)),
            globalTokenBudget = 100,
            lorebooks = mapOf(1L to Lorebook(id = 1L, name = "Book", tokenBudget = 10)),
            tokenizer = tokenizer,
        )

        assertEquals(listOf(first), selection.result.activatedEntries)
        assertEquals(1, selection.omittedItems.size)
    }

    @Test
    fun lorebookBudgetIsAnAbsoluteTokenLimit() {
        // 小于和大于 100 的配置均为 Token 数；全局预算避开 100，以区分百分比误读。
        listOf(25, 101).forEach { budget ->
            val entry = entry(id = 1L, content = "x".repeat(budget + 1), order = 10)
            val selection = fitWorldInfoToBudget(
                result = WorldBookActivationResult(listOf(entry)),
                globalTokenBudget = 1_000,
                lorebooks = mapOf(1L to Lorebook(id = 1L, name = "Book", tokenBudget = budget)),
                tokenizer = tokenizer,
            )

            assertTrue("Budget $budget must be an absolute Token limit", selection.result.activatedEntries.isEmpty())
            assertEquals(PromptOmissionReason.WorldInfoBudget, selection.omittedItems.single().reason)
        }
    }

    @Test
    fun zeroLorebookBudgetFollowsGlobalBudget() {
        val entry = entry(id = 1L, content = "x".repeat(30), order = 10)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(entry)),
            globalTokenBudget = 40,
            lorebooks = mapOf(1L to Lorebook(id = 1L, name = "Book")),
            tokenizer = tokenizer,
        )

        assertEquals(listOf(entry), selection.result.activatedEntries)
        assertTrue(selection.omittedItems.isEmpty())
    }

    @Test
    fun disabledExamplesAreRemovedBeforeBudgetSelection() {
        val example = entry(id = 1L, content = "x".repeat(80), order = 100)
        val normal = entry(id = 2L, content = "n".repeat(50), order = 10)
        val activated = WorldBookActivationResult(
            activatedEntries = listOf(example, normal),
            beforeCharacter = listOf(normal),
            exampleBefore = listOf(example),
        ).filterForExampleBehavior(ExampleDialogueBehavior.Disabled)

        val selection = fitWorldInfoToBudget(
            result = activated,
            globalTokenBudget = 100,
            lorebooks = emptyMap(),
            tokenizer = tokenizer,
        )

        assertEquals(listOf(normal), selection.result.activatedEntries)
        assertTrue(selection.omittedItems.isEmpty())
    }

    @Test
    fun timedStateEntriesFollowFinalPromptSources() {
        val retained = entry(id = 1L, content = "retained", order = 30)
        val omitted = entry(id = 2L, content = "omitted", order = 20)
        val outlet = entry(id = 3L, content = "outlet", order = 10).copy(
            position = LorebookEntry.POSITION_OUTLET,
            outletName = "rules",
        )
        val result = WorldBookActivationResult(
            activatedEntries = listOf(retained, omitted, outlet),
            outletEntries = mapOf("rules" to listOf(outlet)),
        )
        val inspection = PromptInspection(
            model = "test",
            tokenizerName = "test",
            tokenizerStrategy = PromptTokenizerStrategy.ModelAware,
            postProcessingMode = PromptPostProcessingMode.None,
            contextLimit = 100,
            responseReserve = 10,
            promptBudget = 90,
            finalTokenCount = 10,
            items = listOf(
                element = PromptInspectionItem(
                    index = 1,
                    role = LLMMessageRole.System,
                    sources = listOf(
                        element = PromptSource(
                            kind = PromptSourceKind.WorldInfo,
                            detail = retained.name,
                            referenceId = retained.id,
                        ),
                    ),
                    tokenCount = 10,
                    content = retained.content,
                ),
            ),
            omittedItems = emptyList(),
        )

        val stateEntries = result.retainStateEntries(inspection).activatedEntries

        assertEquals(listOf(retained, outlet), stateEntries)
    }

    private fun entry(
        id: Long,
        content: String,
        constant: Boolean = false,
        order: Int,
    ): LorebookEntry {
        return LorebookEntry(
            id = id,
            lorebookId = 1L,
            name = "Entry $id",
            keywords = "[]",
            secondaryKeywords = "[]",
            constant = constant,
            order = order,
            depth = 0,
            category = "[]",
            content = content,
        )
    }
}
