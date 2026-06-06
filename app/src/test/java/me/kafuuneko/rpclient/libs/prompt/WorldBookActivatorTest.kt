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

    @Test
    fun emptyRegexKeywordDoesNotMatchEverything() {
        val emptyRegexEntry = lorebookEntry(id = 1L, keywords = """["//"]""")

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("Any text would match an empty regex.")),
                currentUserMessage = null,
                entries = listOf(emptyRegexEntry)
            )
        )

        assertEquals(emptyList<LorebookEntry>(), activated)
    }

    @Test
    fun secondaryLogicAndAllRequiresEverySecondaryKeyword() {
        val entry = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            secondaryKeywords = """["bell","fog"]""",
            selectiveLogic = LorebookEntry.LOGIC_AND_ALL
        )

        val missing = activator.activate(context(listOf(chatMessage("The harbor bell rang.")), null, listOf(entry)))
        val matched = activator.activate(context(listOf(chatMessage("The harbor bell rang in fog.")), null, listOf(entry)))

        assertEquals(emptyList<LorebookEntry>(), missing)
        assertEquals(listOf(entry), matched)
    }

    @Test
    fun recursiveScanCanActivateEntriesFromEntryContent() {
        val first = lorebookEntry(id = 1L, keywords = """["harbor"]""", content = "The moon gate opens.")
        val second = lorebookEntry(id = 2L, keywords = """["moon gate"]""", content = "Recursive lore")

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("We reached the harbor.")),
                currentUserMessage = null,
                entries = listOf(first, second),
                recursiveScanningLorebookIds = setOf(1L)
            )
        )

        assertEquals(setOf(first, second), activated.toSet())
    }

    @Test
    fun recursiveScanIsNotEnabledByDefault() {
        val first = lorebookEntry(id = 1L, keywords = """["harbor"]""", content = "The moon gate opens.")
        val second = lorebookEntry(id = 2L, keywords = """["moon gate"]""", content = "Recursive lore")

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("We reached the harbor.")),
                currentUserMessage = null,
                entries = listOf(first, second)
            )
        )

        assertEquals(listOf(first), activated)
    }

    @Test
    fun timedEffectsApplyStickyCooldownAndDelay() {
        val entry = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            sticky = 1,
            cooldown = 1,
            delay = 2
        )
        val delayed = activator.activateStructured(
            context(messages = listOf(chatMessage("harbor")), currentUserMessage = null, entries = listOf(entry))
        )
        val activated = activator.activateStructured(
            context(
                messages = listOf(chatMessage("setup"), chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(entry)
            )
        )
        val sticky = activator.activateStructured(
            context(
                messages = listOf(chatMessage("setup"), chatMessage("harbor"), chatMessage("no key")),
                currentUserMessage = null,
                entries = listOf(entry),
                worldInfoStateJson = activated.nextStateJson
            )
        )
        val cooldown = activator.activateStructured(
            context(
                messages = listOf(chatMessage("setup"), chatMessage("harbor"), chatMessage("no key"), chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(entry),
                worldInfoStateJson = sticky.nextStateJson
            )
        )

        assertEquals(emptyList<LorebookEntry>(), delayed.activatedEntries)
        assertEquals(listOf(entry), activated.activatedEntries)
        assertEquals(listOf(entry), sticky.activatedEntries)
        assertEquals(emptyList<LorebookEntry>(), cooldown.activatedEntries)
    }

    @Test
    fun summarizedHistoryDoesNotResetTimedEffects() {
        val entry = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            sticky = 2
        )
        val activated = activator.activateStructured(
            context(
                messages = listOf(chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(entry),
                totalMessageCount = 20
            )
        )

        val afterSummary = activator.activateStructured(
            context(
                messages = listOf(chatMessage("no key")),
                currentUserMessage = null,
                entries = listOf(entry),
                worldInfoStateJson = activated.nextStateJson,
                totalMessageCount = 21
            )
        )

        assertEquals(listOf(entry), afterSummary.activatedEntries)
    }

    @Test
    fun inclusionGroupKeepsOnlyPrioritizedEntry() {
        val low = lorebookEntry(id = 1L, constant = true, order = 10, group = "weather", groupOverride = true)
        val high = lorebookEntry(id = 2L, constant = true, order = 30, group = "weather", groupOverride = true)

        val activated = activator.activate(context(emptyList(), null, listOf(low, high)))

        assertEquals(listOf(high), activated)
    }

    private fun context(
        messages: List<ChatMessage>,
        currentUserMessage: String?,
        entries: List<LorebookEntry>,
        worldInfoStateJson: String = "{}",
        recursiveScanningLorebookIds: Set<Long> = emptySet(),
        totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1
    ): PromptBuildContext {
        return PromptBuildContext(
            userName = "User",
            userDescription = "",
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
                userNote = "",
                creatorNotes = null,
                worldInfoStateJson = worldInfoStateJson
            ),
            summary = "",
            messages = messages,
            currentUserMessage = currentUserMessage,
            totalMessageCount = totalMessageCount,
            candidateLorebookEntries = entries,
            recursiveScanningLorebookIds = recursiveScanningLorebookIds,
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
            content = content
        )
    }

    private fun lorebookEntry(
        id: Long,
        constant: Boolean = false,
        keywords: String = """["key"]""",
        secondaryKeywords: String = "[]",
        selectiveLogic: Int = LorebookEntry.LOGIC_AND_ANY,
        content: String = "Content $id",
        order: Int = id.toInt(),
        sticky: Int? = null,
        cooldown: Int? = null,
        delay: Int? = null,
        group: String = "",
        groupOverride: Boolean = false
    ): LorebookEntry {
        return LorebookEntry(
            id = id,
            lorebookId = 1L,
            name = "Entry $id",
            keywords = keywords,
            secondaryKeywords = secondaryKeywords,
            constant = constant,
            order = order,
            depth = 0,
            category = "[]",
            content = content,
            selectiveLogic = selectiveLogic,
            sticky = sticky,
            cooldown = cooldown,
            delay = delay,
            group = group,
            groupOverride = groupOverride
        )
    }
}
