package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
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

    @Test
    fun triggersFilterGenerationTypeAndNeverActAsKeywords() {
        val entry = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            triggers = """["continue"]"""
        )

        val normal = activator.activate(
            context(
                messages = listOf(chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(entry)
            )
        )
        val continueWithoutKeyword = activator.activate(
            context(
                messages = listOf(chatMessage("continue")),
                currentUserMessage = null,
                entries = listOf(entry),
                generationMode = PromptGenerationMode.Continue
            )
        )
        val continued = activator.activate(
            context(
                messages = listOf(chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(entry),
                generationMode = PromptGenerationMode.Continue
            )
        )

        assertEquals(emptyList<LorebookEntry>(), normal)
        assertEquals(emptyList<LorebookEntry>(), continueWithoutKeyword)
        assertEquals(listOf(entry), continued)
    }

    @Test
    fun wholeWordMatchingIsEnabledByDefault() {
        val entry = lorebookEntry(id = 1L, keywords = """["king"]""")

        val substring = activator.activate(
            context(listOf(chatMessage("This is not to my liking.")), null, listOf(entry))
        )
        val wholeWord = activator.activate(
            context(listOf(chatMessage("Long live the king.")), null, listOf(entry))
        )

        assertEquals(emptyList<LorebookEntry>(), substring)
        assertEquals(listOf(entry), wholeWord)
    }

    @Test
    fun scanBufferIncludesSpeakerNamesByDefault() {
        val entry = lorebookEntry(id = 1L, keywords = """["User"]""")

        val activated = activator.activate(
            context(listOf(chatMessage("Hello there.")), null, listOf(entry))
        )

        assertEquals(listOf(entry), activated)
    }

    @Test
    fun javascriptStyleRegexFlagsAreApplied() {
        val entry = lorebookEntry(id = 1L, keywords = """["/HARBOR/i"]""")

        val activated = activator.activate(
            context(listOf(chatMessage("The harbor bell rang.")), null, listOf(entry))
        )

        assertEquals(listOf(entry), activated)
    }

    @Test
    fun regexSupportsEscapedSlashAndMultilineFlags() {
        val slashEntry = lorebookEntry(id = 1L, keywords = """["/harbor\\/bell/i"]""")
        val multilineEntry = lorebookEntry(id = 2L, keywords = """["/^bell/m"]""")
        val message = chatMessage("The HARBOR/bell rang.\nbell again.")

        val activated = activator.activate(
            context(listOf(message), null, listOf(slashEntry, multilineEntry))
        )

        assertEquals(listOf(multilineEntry, slashEntry), activated)
    }

    @Test
    fun stickyRegexOnlyMatchesAtStartOfScanBuffer() {
        val stickyEntry = lorebookEntry(id = 1L, keywords = """["/.User/y"]""")
        val laterEntry = lorebookEntry(id = 2L, keywords = """["/Hello/y"]""")

        val activated = activator.activate(
            context(listOf(chatMessage("Hello there.")), null, listOf(stickyEntry, laterEntry))
        )

        assertEquals(listOf(stickyEntry), activated)
    }

    @Test
    fun scanBufferStartsWithNewestMessageAndCountsCurrentUserMessageInDepth() {
        val entry = lorebookEntry(id = 1L, keywords = """["/.User: newest/y"]""")
        val book = Lorebook(id = 1L, name = "Book", scanDepth = 1)

        val fromHistory = activator.activate(
            context(
                messages = listOf(chatMessage("older"), chatMessage("newest")),
                currentUserMessage = null,
                entries = listOf(entry),
                lorebooks = mapOf(1L to book)
            )
        )
        val fromCurrentUser = activator.activate(
            context(
                messages = listOf(chatMessage("older")),
                currentUserMessage = "newest",
                entries = listOf(entry),
                lorebooks = mapOf(1L to book)
            )
        )

        assertEquals(listOf(entry), fromHistory)
        assertEquals(listOf(entry), fromCurrentUser)
    }

    @Test
    fun invalidJavascriptRegexFlagsDoNotExecuteAsRegex() {
        val duplicateFlag = lorebookEntry(id = 1L, keywords = """["/harbor/ii"]""")
        val unsupportedFlag = lorebookEntry(id = 2L, keywords = """["/harbor/v"]""")
        val unescapedDelimiter = lorebookEntry(id = 3L, keywords = """["/harbor/bell/i"]""")

        val activated = activator.activate(
            context(
                listOf(chatMessage("The harbor bell rang.")),
                null,
                listOf(duplicateFlag, unsupportedFlag, unescapedDelimiter)
            )
        )

        assertEquals(emptyList<LorebookEntry>(), activated)
    }

    @Test
    fun whitespaceRegexPatternRemainsValid() {
        val entry = lorebookEntry(id = 1L, keywords = """["/ +/"]""")

        val activated = activator.activate(
            context(listOf(chatMessage("two words")), null, listOf(entry))
        )

        assertEquals(listOf(entry), activated)
    }

    @Test
    fun entryWithoutDepthUsesLorebookScanDepth() {
        val entry = lorebookEntry(id = 1L, keywords = """["harbor"]""")
        val shallowBook = Lorebook(id = 1L, name = "Book", scanDepth = 1)
        val deepBook = shallowBook.copy(scanDepth = 2)
        val messages = listOf(chatMessage("harbor"), chatMessage("latest"))

        val shallow = activator.activate(
            context(
                messages = messages,
                currentUserMessage = null,
                entries = listOf(entry),
                lorebooks = mapOf(1L to shallowBook)
            )
        )
        val deep = activator.activate(
            context(
                messages = messages,
                currentUserMessage = null,
                entries = listOf(entry),
                lorebooks = mapOf(1L to deepBook)
            )
        )

        assertEquals(emptyList<LorebookEntry>(), shallow)
        assertEquals(listOf(entry), deep)
    }

    @Test
    fun multipleInclusionGroupsRemoveAllCompetingEntries() {
        val winner = lorebookEntry(
            id = 1L,
            constant = true,
            order = 30,
            group = "weather, scene",
            groupOverride = true
        )
        val weather = lorebookEntry(id = 2L, constant = true, group = "weather")
        val scene = lorebookEntry(id = 3L, constant = true, group = "scene")

        val activated = activator.activate(
            context(emptyList(), null, listOf(winner, weather, scene))
        )

        assertEquals(listOf(winner), activated)
    }

    @Test
    fun groupScoringKeepsEntryWithMostMatchedKeys() {
        val general = lorebookEntry(
            id = 1L,
            keywords = """["song","sing"]""",
            group = "songs",
            useGroupScoring = true
        )
        val specific = lorebookEntry(
            id = 2L,
            keywords = """["song","sing","Ghosts"]""",
            group = "songs",
            useGroupScoring = true
        )

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("Sing me a song about Ghosts.")),
                currentUserMessage = null,
                entries = listOf(general, specific)
            )
        )

        assertEquals(listOf(specific), activated)
    }

    @Test
    fun inclusionGroupLoserCannotTriggerRecursiveEntry() {
        val winner = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            content = "Winner content",
            order = 20,
            group = "scene",
            groupOverride = true
        )
        val loser = lorebookEntry(
            id = 2L,
            keywords = """["harbor"]""",
            content = "secret recursion key",
            order = 10,
            group = "scene"
        )
        val recursive = lorebookEntry(
            id = 3L,
            keywords = """["secret recursion key"]""",
            content = "Must stay inactive"
        )

        val activated = activator.activate(
            context(
                messages = listOf(chatMessage("harbor")),
                currentUserMessage = null,
                entries = listOf(winner, loser, recursive),
                recursiveScanningLorebookIds = setOf(1L)
            )
        )

        assertEquals(listOf(winner), activated)
    }

    @Test
    fun prioritizedGroupWinnerThatFailsProbabilityDoesNotEnableFallback() {
        val winner = lorebookEntry(
            id = 1L,
            constant = true,
            order = 20,
            probability = 0,
            group = "scene",
            groupOverride = true
        )
        val fallback = lorebookEntry(
            id = 2L,
            constant = true,
            order = 10,
            probability = 100,
            group = "scene"
        )

        val activated = activator.activate(
            context(emptyList(), null, listOf(winner, fallback))
        )

        assertEquals(emptyList<LorebookEntry>(), activated)
    }

    @Test
    fun budgetOmittedEntryDoesNotEnterTimedState() {
        val entry = lorebookEntry(
            id = 1L,
            keywords = """["harbor"]""",
            content = "Too large",
            sticky = 2
        )
        val activated = activator.activateStructured(
            context(listOf(chatMessage("harbor")), null, listOf(entry))
        )
        val budgeted = fitWorldInfoToBudget(
            result = activated,
            globalTokenBudget = 0,
            promptTokenBudget = 100,
            lorebooks = emptyMap(),
            tokenizer = object : PromptTokenizer {
                override val name: String = "Length"
                override val strategy: PromptTokenizerStrategy =
                    PromptTokenizerStrategy.ModelAware

                override fun countText(text: String): Int = text.length
            }
        )
        val resolved = activator.resolveNextState(budgeted.result)
        val nextTurn = activator.activateStructured(
            context(
                messages = listOf(chatMessage("No matching key")),
                currentUserMessage = null,
                entries = listOf(entry),
                worldInfoStateJson = resolved.nextStateJson
            )
        )

        assertEquals(emptyList<LorebookEntry>(), resolved.activatedEntries)
        assertEquals(emptyList<LorebookEntry>(), nextTurn.activatedEntries)
    }

    private fun context(
        messages: List<ChatMessage>,
        currentUserMessage: String?,
        entries: List<LorebookEntry>,
        worldInfoStateJson: String = "{}",
        recursiveScanningLorebookIds: Set<Long> = emptySet(),
        totalMessageCount: Int = messages.size + if (currentUserMessage.isNullOrBlank()) 0 else 1,
        lorebooks: Map<Long, Lorebook> = emptyMap(),
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal
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
            candidateLorebooks = lorebooks,
            recursiveScanningLorebookIds = recursiveScanningLorebookIds,
            provider = null,
            maxContextTokens = 4096,
            maxResponseTokens = 512,
            generationMode = generationMode
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
        probability: Int = 100,
        group: String = "",
        groupOverride: Boolean = false,
        useGroupScoring: Boolean = false,
        triggers: String = "[]"
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
            probability = probability,
            group = group,
            groupOverride = groupOverride,
            useGroupScoring = useGroupScoring,
            triggers = triggers
        )
    }
}
