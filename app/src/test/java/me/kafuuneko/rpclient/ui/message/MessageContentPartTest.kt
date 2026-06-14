package me.kafuuneko.rpclient.ui.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContentPartTest {
    @Test
    fun splitsThinkBlocksFromVisibleText() {
        val parts = "hello<think>reasoning</think>world".toMessageContentParts("42")

        assertEquals(MessageContentPart.Text("hello"), parts[0])
        assertEquals(MessageContentPart.Think("42:0", "reasoning"), parts[1])
        assertEquals(MessageContentPart.Text("world"), parts[2])
    }

    @Test
    fun marksUnclosedThinkBlockAsIncomplete() {
        val parts = "<think>reasoning".toMessageContentParts("42")

        assertEquals(
            MessageContentPart.Think("42:0", "reasoning", isComplete = false),
            parts.single()
        )
    }

    @Test
    fun hidesEmptyStreamingThinkTag() {
        assertTrue("<think>".toMessageContentParts("42").isEmpty())
    }

    @Test
    fun ignoresBlankAndNullThinkBlocks() {
        val parts = "hello<think>null</think><think>   </think>".toMessageContentParts("42")

        assertEquals(listOf(MessageContentPart.Text("hello")), parts)
    }
}
