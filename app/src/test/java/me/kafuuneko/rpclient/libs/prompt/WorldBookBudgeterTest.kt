package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Lorebook
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
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
    fun constantEntriesAreBudgetedBeforeHigherOrderNormalEntries() {
        val constant = entry(id = 1L, content = "CCCC", constant = true, order = 1)
        val normal = entry(id = 2L, content = "NNNN", constant = false, order = 100)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(normal, constant)),
            globalTokenBudget = 6,
            promptTokenBudget = 100,
            lorebooks = emptyMap(),
            tokenizer = tokenizer
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
            promptTokenBudget = 100,
            lorebooks = mapOf(
                1L to Lorebook(id = 1L, name = "Book", tokenBudget = 10)
            ),
            tokenizer = tokenizer
        )

        assertEquals(listOf(first), selection.result.activatedEntries)
        assertEquals(1, selection.omittedItems.size)
    }

    @Test
    fun largeLorebookBudgetIsAnAbsoluteTokenLimit() {
        val entry = entry(id = 1L, content = "x".repeat(102), order = 10)

        val selection = fitWorldInfoToBudget(
            result = WorldBookActivationResult(listOf(entry)),
            globalTokenBudget = 1_000,
            promptTokenBudget = 1_000,
            lorebooks = mapOf(
                1L to Lorebook(id = 1L, name = "Book", tokenBudget = 101)
            ),
            tokenizer = tokenizer
        )

        assertEquals(emptyList<LorebookEntry>(), selection.result.activatedEntries)
    }

    @Test
    fun timedStateEntriesFollowFinalPromptSources() {
        val retained = entry(id = 1L, content = "retained", order = 30)
        val omitted = entry(id = 2L, content = "omitted", order = 20)
        val outlet = entry(id = 3L, content = "outlet", order = 10).copy(
            position = LorebookEntry.POSITION_OUTLET,
            outletName = "rules"
        )
        val result = WorldBookActivationResult(
            activatedEntries = listOf(retained, omitted, outlet),
            outletEntries = mapOf("rules" to listOf(outlet))
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
                PromptInspectionItem(
                    index = 1,
                    role = LLMMessageRole.System,
                    sources = listOf(
                        PromptSource(
                            kind = PromptSourceKind.WorldInfo,
                            detail = retained.name,
                            referenceId = retained.id
                        )
                    ),
                    tokenCount = 10,
                    content = retained.content
                )
            ),
            omittedItems = emptyList()
        )

        val stateEntries = result.retainStateEntries(inspection).activatedEntries

        assertEquals(listOf(retained, outlet), stateEntries)
    }

    private fun entry(
        id: Long,
        content: String,
        constant: Boolean = false,
        order: Int
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
            content = content
        )
    }
}
