package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPromptBuilderTest {
    private val historyBuilder = FormattedHistoryBuilder()
    private val builder = ChatPromptBuilder(
        mMacroResolver = PromptMacroResolver(historyBuilder),
        mHistoryBuilder = historyBuilder,
        mWorldBookActivator = WorldBookActivator()
    )

    @Test
    fun userNoteIsInsertedBeforeCurrentUserMessage() {
        val request = builder.build(
            context(
                session = session(userNote = "User note"),
                messages = listOf(
                    chatMessage(id = 1L, source = ChatMessage.Source.Char, content = "Older reply"),
                    chatMessage(id = 2L, source = ChatMessage.Source.User, content = "Current user")
                )
            )
        )

        assertEquals(
            listOf("Older reply", "User note", "Current user"),
            request.messages
                .filter { it.content in setOf("Older reply", "User note", "Current user") }
                .map { it.content }
        )
        assertEquals(LLMMessageRole.System, request.messages.first { it.content == "User note" }.role)
    }

    @Test
    fun worldInfoDepthControlsInChatInsertionPoint() {
        val request = builder.build(
            context(
                messages = listOf(
                    chatMessage(id = 1L, source = ChatMessage.Source.User, content = "Message 1"),
                    chatMessage(id = 2L, source = ChatMessage.Source.Char, content = "Message 2"),
                    chatMessage(id = 3L, source = ChatMessage.Source.User, content = "Message 3")
                ),
                entries = listOf(
                    lorebookEntry(id = 1L, order = 100, depth = 0, content = "Depth 0"),
                    lorebookEntry(id = 2L, order = 100, depth = 1, content = "Depth 1"),
                    lorebookEntry(id = 3L, order = 100, depth = 2, content = "Depth 2")
                )
            )
        )

        assertEquals(
            listOf("Message 1", "Depth 2", "Message 2", "Depth 1", "Message 3", "Depth 0"),
            request.messages
                .filter { it.content in setOf("Message 1", "Message 2", "Message 3", "Depth 0", "Depth 1", "Depth 2") }
                .map { it.content }
        )
    }

    @Test
    fun creatorNotesStayMetadataButDepthPromptIsInjected() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    creatorNotes = "Creator metadata",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    depthPromptPrompt = "Character note",
                    depthPromptDepth = 1
                ),
                messages = listOf(
                    chatMessage(id = 1L, source = ChatMessage.Source.User, content = "Hello")
                )
            )
        )

        assertEquals(false, request.messages.any { it.content.contains("Creator metadata") })
        assertEquals(true, request.messages.any { it.content == "Character note" })
    }

    @Test
    fun outletEntriesResolveIntoPromptMacros() {
        val request = builder.build(
            context(
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
                    postHistoryInstructions = "",
                    systemPrompt = "{{outlet::rules}}\n{{original}}"
                ),
                entries = listOf(
                    lorebookEntry(
                        id = 10L,
                        order = 100,
                        depth = 4,
                        content = "Outlet lore",
                        position = LorebookEntry.POSITION_OUTLET,
                        outletName = "rules"
                    )
                )
            )
        )

        assertEquals(true, request.messages.first().content.contains("Outlet lore"))
    }

    @Test
    fun alternateGreetingsResolveAsFirstMessageChoices() {
        val resolved = PromptMacroResolver(historyBuilder).resolve(
            template = "{{charFirstMessage}}|{{charFirstMessage::0}}|{{charFirstMessage::1}}|{{charFirstMessage::2}}",
            context = context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    creatorNotes = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "Primary greeting",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    alternateGreetings = """["Alt one"," Alt two "]"""
                )
            )
        )

        assertEquals("Primary greeting|Primary greeting|Alt one|Alt two", resolved)
    }

    @Test
    fun nonConstantWorldInfoWithoutKeywordMatchIsNotInjected() {
        val request = builder.build(
            context(
                messages = listOf(
                    chatMessage(id = 1L, source = ChatMessage.Source.User, content = "Nothing relevant here")
                ),
                entries = listOf(
                    lorebookEntry(
                        id = 20L,
                        order = 100,
                        depth = 4,
                        content = "Should stay out",
                        keywords = """["harbor"]""",
                        constant = false
                    )
                )
            )
        )

        assertEquals(false, request.messages.any { it.content == "Should stay out" })
    }

    private fun context(
        character: Character = Character(
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
        session: ChatSession = session(),
        messages: List<ChatMessage> = emptyList(),
        entries: List<LorebookEntry> = emptyList()
    ): PromptBuildContext {
        return PromptBuildContext(
            userName = "User",
            userDescription = "",
            character = character,
            session = session,
            messages = messages,
            currentUserMessage = null,
            candidateLorebookEntries = entries,
            provider = null,
            maxContextTokens = 4096,
            maxResponseTokens = 512
        )
    }

    private fun session(userNote: String = ""): ChatSession {
        return ChatSession(
            id = 1L,
            characterId = 1L,
            createTime = 0L,
            latestTime = 0L,
            lorebookEntrySet = "[]",
            title = "",
            summarize = "",
            userNote = userNote,
            creatorNotes = null
        )
    }

    private fun chatMessage(id: Long, source: ChatMessage.Source, content: String): ChatMessage {
        return ChatMessage(
            id = id,
            sessionId = 1L,
            createTime = id,
            source = source,
            content = content,
            isSummarized = false
        )
    }

    private fun lorebookEntry(
        id: Long,
        order: Int,
        depth: Int,
        content: String,
        position: Int = LorebookEntry.POSITION_AT_DEPTH,
        outletName: String = "",
        keywords: String = "[]",
        constant: Boolean = true
    ): LorebookEntry {
        return LorebookEntry(
            id = id,
            lorebookId = 1L,
            name = "Entry $id",
            keywords = keywords,
            secondaryKeywords = "[]",
            constant = constant,
            order = order,
            depth = depth,
            category = "[]",
            content = content,
            position = position,
            outletName = outletName
        )
    }
}
