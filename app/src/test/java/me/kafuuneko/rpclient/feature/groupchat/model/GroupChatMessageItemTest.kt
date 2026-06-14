package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.ui.message.MessageContentPart
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatMessageItemTest {
    @Test
    fun buildsTextAndReasoningPartsFromContent() {
        val item = GroupChatMessageItem(
            id = 7,
            source = GroupChatMessage.Source.Character,
            speakerName = "Lyra",
            content = "hello<think>reasoning</think>world",
            time = "12:00"
        )

        assertEquals(
            listOf(
                MessageContentPart.Text("hello"),
                MessageContentPart.Think("7:0", "reasoning"),
                MessageContentPart.Text("world")
            ),
            item.parts
        )
    }

    @Test
    fun marksStreamingReasoningAsIncomplete() {
        val item = GroupChatMessageItem(
            id = 8,
            source = GroupChatMessage.Source.Character,
            speakerName = "Lyra",
            content = "<think>reasoning",
            time = "12:00",
            isStreaming = true
        )

        assertEquals(
            MessageContentPart.Think("8:0", "reasoning", isComplete = false),
            item.parts.single()
        )
    }
}
