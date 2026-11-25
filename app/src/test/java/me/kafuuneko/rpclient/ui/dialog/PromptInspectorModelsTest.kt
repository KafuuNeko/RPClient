package me.kafuuneko.rpclient.ui.dialog

import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.model.countInspectorMatches
import me.kafuuneko.rpclient.model.groupPromptSources
import me.kafuuneko.rpclient.model.splitInspectorTextChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptInspectorModelsTest {
    @Test
    fun groupPromptSourcesMergesSameKindInFirstSeenOrder() {
        val sources = listOf(
            PromptSource(PromptSourceKind.MainPrompt),
            PromptSource(PromptSourceKind.WorldInfo, "First lore", 1L),
            PromptSource(PromptSourceKind.CharacterDescription),
            PromptSource(PromptSourceKind.WorldInfo, "Second lore", 2L),
            PromptSource(PromptSourceKind.WorldInfo, "Third lore", 3L),
        )

        val groups = groupPromptSources(sources)

        assertEquals(
            listOf(
                PromptSourceKind.MainPrompt,
                PromptSourceKind.WorldInfo,
                PromptSourceKind.CharacterDescription,
            ),
            groups.map { group -> group.kind },
        )
        assertEquals(
            listOf("First lore", "Second lore", "Third lore"),
            groups[1].sources.map { source -> source.detail },
        )
    }

    @Test
    fun splitInspectorTextChunksHandlesMultiLineAndSingleUltraLongLine() {
        val multiLine = (1..55).joinToString("\n") { line -> "Line $line content" }
        val multiChunks = splitInspectorTextChunks(multiLine)
        assertTrue(multiChunks.size >= 3)
        assertEquals(multiLine, multiChunks.joinToString("\n"))

        val ultraLongSingleLine = "A".repeat(2_850)
        val longLineChunks = splitInspectorTextChunks(ultraLongSingleLine)
        assertEquals(3, longLineChunks.size)
        assertTrue(longLineChunks.all { chunk -> chunk.length <= 1_200 })
        assertEquals(ultraLongSingleLine, longLineChunks.joinToString(""))
    }

    @Test
    fun countInspectorMatchesIsCaseInsensitiveAndNonOverlapping() {
        assertEquals(3, countInspectorMatches("Rain in RAINY rain", "rain"))
        assertEquals(2, countInspectorMatches("aaaa", "aa"))
        assertEquals(0, countInspectorMatches("sample text", "   "))
    }
}
