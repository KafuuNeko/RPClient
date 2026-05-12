package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldBookActivatorTest {
    private val activator = WorldBookActivator()

    @Test
    fun constantEntryActivatesWithoutKeywordMatch() {
        val constantEntry = lorebookEntry(id = 1L, constant = true, keywords = "[]")
        val keywordEntry = lorebookEntry(id = 2L, constant = false, keywords = """["harbor"]""")

        val activated = activator.activate(
            context(
                messages = emptyList(),
                currentUserMessage = null,
                entries = listOf(constantEntry, keywordEntry)
            )
        )

        assertEquals(listOf(constantEntry), activated)
    }

    @Test
    fun keywordEntryStillRequiresPrimaryKeyword() {
        val keywordEntry = lorebookEntry(id = 1L, keywords = """["harbor"]""")

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("The harbor bell rang.")),
                currentUserMessage = null,
                entries = listOf(keywordEntry)
            )
        )

        assertEquals(listOf(keywordEntry), activated)
    }

    private fun context(
        messages: List<ChatMessage>,
        currentUserMessage: String?,
        entries: List<LorebookEntry>
    ): PromptBuildContext {
        return PromptBuildContext(
            userName = "User",
            character = Character(
                id = 1L,
                name = "Char",
                avatar = "",
                characterTags = "[]",
                description = "",
                creatorNotes = "",
                personality = "",
                scenario = "",
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            ),
            session = ChatSession(
                id = 1L,
                characterId = 1L,
                createTime = 0L,
                latestTime = 0L,
                lorebookEntrySet = "[]",
                title = "",
                summarize = "",
                userNote = "",
                creatorNotes = null
            ),
            messages = messages,
            currentUserMessage = currentUserMessage,
            candidateLorebookEntries = entries,
            provider = null,
            maxContextTokens = 4096,
            maxResponseTokens = 512
        )
    }

    private fun chatMessage(content: String): ChatMessage {
        return ChatMessage(
            id = 1L,
            sessionId = 1L,
            createTime = 0L,
            source = ChatMessage.Source.User,
            content = content,
            isSummarized = false
        )
    }

    private fun lorebookEntry(
        id: Long,
        constant: Boolean = false,
        keywords: String = """["key"]"""
    ): LorebookEntry {
        return LorebookEntry(
            id = id,
            lorebookId = 1L,
            name = "Entry $id",
            keywords = keywords,
            secondaryKeywords = "[]",
            constant = constant,
            order = id.toInt(),
            depth = 0,
            category = "[]",
            content = "Content $id"
        )
    }
}
