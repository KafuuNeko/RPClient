package me.kafuuneko.rpclient.feature.chat.utils

import me.kafuuneko.rpclient.feature.chat.model.ChatMessageContentPart
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiMappersTest {
    @Test
    fun toChatSessionItem_keepsSessionUserIdentity() {
        val item = ChatSession(
            id = 1,
            characterId = 2,
            createTime = 3,
            latestTime = 4,
            lorebookEntrySet = "[]",
            title = "Session",
            userNote = "",
            userName = "Alice",
            userDescription = "An investigator"
        ).toChatSessionItem(
            summary = "",
            creatorNotes = "",
            messageCount = 0,
            enabledIds = emptySet()
        )

        assertEquals("Alice", item.userName)
        assertEquals("An investigator", item.userDescription)
    }

    @Test
    fun toContentParts_splitsThinkBlocksFromVisibleText() {
        val parts = "hello<think>reasoning</think>world".toContentParts("42")

        assertEquals(ChatMessageContentPart.Text("hello"), parts[0])
        assertEquals(ChatMessageContentPart.Think("42:0", "reasoning"), parts[1])
        assertEquals(ChatMessageContentPart.Text("world"), parts[2])
    }

    @Test
    fun toContentParts_marksUnclosedThinkBlockAsIncomplete() {
        val parts = "<think>reasoning".toContentParts("42")

        assertEquals(
            ChatMessageContentPart.Think("42:0", "reasoning", isComplete = false),
            parts.single()
        )
    }

    @Test
    fun toContentParts_hidesEmptyStreamingThinkTag() {
        assertTrue("<think>".toContentParts("42").isEmpty())
    }

    @Test
    fun toContentParts_ignoresBlankAndNullThinkBlocks() {
        val parts = "hello<think>null</think><think>   </think>".toContentParts("42")

        assertEquals(listOf(ChatMessageContentPart.Text("hello")), parts)
    }

    @Test
    fun replaceStreamingMessage_updatesOnlyMatchingMessage() {
        val messages = listOf(
            listOf(chatMessage(1, ChatMessage.Source.User, "old")).toChatMessageItems(
                characterName = "Char",
                userName = "User",
                systemSpeaker = "System",
                streamingMessageId = null
            ).single(),
            listOf(chatMessage(2, ChatMessage.Source.Char, "old")).toChatMessageItems(
                characterName = "Char",
                userName = "User",
                systemSpeaker = "System",
                streamingMessageId = null
            ).single()
        )

        val updated = messages.replaceStreamingMessage(2, "new")

        assertEquals("old", updated[0].content)
        assertFalse(updated[0].isStreaming)
        assertEquals("new", updated[1].content)
        assertTrue(updated[1].isStreaming)
    }

    @Test
    fun toChatMessageItems_mapsSpeakersAndRoles() {
        val items = listOf(
            chatMessage(1, ChatMessage.Source.User, "u"),
            chatMessage(2, ChatMessage.Source.Char, "c"),
            chatMessage(3, ChatMessage.Source.System, "s")
        ).toChatMessageItems(
            characterName = "Char",
            userName = "User",
            systemSpeaker = "System",
            streamingMessageId = 2
        )

        assertEquals(MessageRole.User, items[0].role)
        assertEquals("User", items[0].speaker)
        assertEquals(MessageRole.Assistant, items[1].role)
        assertEquals("Char", items[1].speaker)
        assertTrue(items[1].isStreaming)
        assertEquals(MessageRole.Narrator, items[2].role)
        assertEquals("System", items[2].speaker)
    }

    private fun chatMessage(
        id: Long,
        source: ChatMessage.Source,
        content: String
    ): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = 1,
            createTime = 0,
            source = source,
            content = content
        )
    }
}
