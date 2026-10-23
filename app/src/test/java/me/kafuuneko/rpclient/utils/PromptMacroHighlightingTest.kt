package me.kafuuneko.rpclient.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptMacroHighlightingTest {

    private val colors = PromptHighlightColors(
        macroForeground = Color.Red,
        macroBackground = Color.Yellow,
        tagForeground = Color.Blue,
        tagBackground = Color.Cyan,
        sectionForeground = Color.Green,
    )

    @Test
    fun `filter highlights tags sections and all supported macro forms`() {
        val input = "[Chat]\n--- CONTEXT ---\n{{char}} meets {0} at <START>."

        val output = PromptMacroVisualTransformation(colors)
            .filter(AnnotatedString(input))
            .text

        assertEquals(input, output.text)
        assertTrue(output.hasStyle(start = 0, end = 6, foreground = Color.Blue))
        assertTrue(output.hasStyle(start = 7, end = 22, foreground = Color.Green))
        assertTrue(output.hasStyle(start = 23, end = 31, foreground = Color.Red))
        assertTrue(output.hasStyle(start = 38, end = 41, foreground = Color.Red))
        assertTrue(output.hasStyle(start = 45, end = 52, foreground = Color.Red))
    }

    private fun AnnotatedString.hasStyle(
        start: Int,
        end: Int,
        foreground: Color,
    ): Boolean = spanStyles.any { range ->
        range.start == start && range.end == end && range.item.color == foreground
    }
}
