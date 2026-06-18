package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatMessage
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptBuilderTest {
    private val historyBuilder = FormattedHistoryBuilder()
    private val builder = ChatPromptBuilder(
        mMacroResolver = PromptMacroResolver(historyBuilder),
        mHistoryBuilder = historyBuilder,
        mWorldBookActivator = WorldBookActivator()
    )

    @Test
    fun promptOnlyRegexChangesOutboundHistoryAndIsInspected() {
        val script = ScopedRegexScript(
            script = RegexScript(
                id = "prompt",
                scriptName = "Prompt rewrite",
                findRegex = "/secret/g",
                replaceString = "visible",
                placement = listOf(RegexPlacement.UserInput.value),
                promptOnly = true
            ),
            scope = RegexScriptScope.Global
        )

        val result = builder.buildWithMetadata(
            context(
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "secret")
                ),
                regexScripts = listOf(script)
            )
        )

        assertTrue(result.request.messages.any { it.content == "visible" })
        assertEquals(listOf("prompt"), result.inspection.regexExecutions.map { it.scriptId })
        assertTrue(
            result.inspection.cacheNotes.any {
                it.kind == PromptCacheNoteKind.RegexPromptRewrite
            }
        )
    }

    @Test
    fun dynamicMacrosAreMarkedForPromptCacheInspection() {
        val result = builder.buildWithMetadata(
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
                    systemPrompt = "Current time: {{time}}"
                )
            )
        )

        assertTrue(
            result.inspection.items.any { item ->
                item.cacheNotes.any {
                    it.kind == PromptCacheNoteKind.DynamicMacro && it.detail == "time"
                }
            }
        )
    }

    @Test
    fun worldInfoPromptRegexDoesNotInvalidateStickySignature() {
        val entry = lorebookEntry(
            id = 9L,
            order = 100,
            depth = 0,
            content = "raw lore",
            keywords = """["harbor"]""",
            constant = false
        ).copy(sticky = 2, position = LorebookEntry.POSITION_BEFORE)
        val script = ScopedRegexScript(
            RegexScript(
                id = "world",
                scriptName = "World rewrite",
                findRegex = "/raw/g",
                replaceString = "regexed",
                placement = listOf(RegexPlacement.WorldInfo.value),
                promptOnly = true
            ),
            RegexScriptScope.Global
        )
        val first = builder.buildWithMetadata(
            context(
                messages = listOf(chatMessage(1L, ChatMessage.Source.User, "harbor")),
                entries = listOf(entry),
                regexScripts = listOf(script)
            )
        )
        val second = builder.buildWithMetadata(
            context(
                session = session().copy(worldInfoStateJson = first.worldInfoStateJson),
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "harbor"),
                    chatMessage(2L, ChatMessage.Source.Char, "No key")
                ),
                entries = listOf(entry),
                regexScripts = listOf(script)
            )
        )

        assertTrue(second.request.messages.any { it.content.contains("regexed lore") })
    }

    @Test
    fun fixedPromptSectionsFollowMainAndCharacterWorldInfoOrder() {
        val result = builder.buildWithMetadata(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "Character description",
                    creatorNotes = "",
                    personality = "Character personality",
                    scenario = "Character scenario",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    systemPrompt = "Main prompt"
                ),
                userDescription = "User persona",
                entries = listOf(
                    lorebookEntry(
                        id = 10L,
                        order = 100,
                        depth = 0,
                        content = "Before character",
                        position = LorebookEntry.POSITION_BEFORE
                    ),
                    lorebookEntry(
                        id = 20L,
                        order = 100,
                        depth = 0,
                        content = "After character",
                        position = LorebookEntry.POSITION_AFTER
                    )
                )
            )
        )

        val relevantKinds = setOf(
            PromptSourceKind.MainPrompt,
            PromptSourceKind.WorldInfo,
            PromptSourceKind.UserPersona,
            PromptSourceKind.CharacterDescription,
            PromptSourceKind.CharacterPersonality,
            PromptSourceKind.Scenario
        )
        val sourceOrder = result.inspection.items
            .flatMap { it.sources }
            .filter { it.kind in relevantKinds }
        assertEquals(
            listOf(
                PromptSourceKind.MainPrompt,
                PromptSourceKind.WorldInfo,
                PromptSourceKind.UserPersona,
                PromptSourceKind.CharacterDescription,
                PromptSourceKind.CharacterPersonality,
                PromptSourceKind.Scenario,
                PromptSourceKind.WorldInfo
            ),
            sourceOrder.map { it.kind }
        )
        assertEquals(
            listOf(10L, 20L),
            sourceOrder.filter { it.kind == PromptSourceKind.WorldInfo }.map { it.referenceId }
        )
    }

    @Test
    fun userNoteUsesDefaultDepthFour() {
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
            listOf("User note", "Older reply", "Current user"),
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
    fun outletEntriesKeepHigherOrderContentFirst() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    systemPrompt = "{{outlet::rules}}"
                ),
                entries = listOf(
                    lorebookEntry(
                        id = 10L,
                        order = 10,
                        depth = 0,
                        content = "Low order",
                        position = LorebookEntry.POSITION_OUTLET,
                        outletName = "rules"
                    ),
                    lorebookEntry(
                        id = 20L,
                        order = 100,
                        depth = 0,
                        content = "High order",
                        position = LorebookEntry.POSITION_OUTLET,
                        outletName = "rules"
                    )
                )
            )
        )

        assertTrue(
            request.messages.first().content.indexOf("High order") <
                request.messages.first().content.indexOf("Low order")
        )
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

    @Test
    fun impersonateModeRemovesCharacterReplyTasksAndEndsWithUserControl() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Fuka",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    creatorNotes = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "Only write Fuka.",
                    systemPrompt = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.",
                    depthPromptPrompt = "Always write as Fuka.",
                    depthPromptDepth = 0
                ),
                generationMode = PromptGenerationMode.Impersonate
            )
        )

        assertFalse(request.messages.any { it.content.contains("Write Fuka's next reply") })
        assertFalse(request.messages.any { it.content == "Only write Fuka." })
        assertFalse(request.messages.any { it.content == "Always write as Fuka." })
        val nudgeMessage = request.messages.first { it.content.contains("point of view of User") }
        assertEquals(LLMMessageRole.User, nudgeMessage.role)
        assertEquals(nudgeMessage, request.messages.last())
    }

    @Test
    fun continueFallbackRemovesCharacterReplyTasksAndEndsWithUserNudge() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "Post history",
                    systemPrompt = "Write Char's next reply."
                ),
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "Question"),
                    chatMessage(2L, ChatMessage.Source.Char, "Partial answer")
                ),
                generationMode = PromptGenerationMode.Continue
            )
        )

        val nudgeMessage = request.messages.first { it.content.contains("Continue your last message") }
        val relevant = request.messages.filter {
            it.content in setOf("Question", "Post history", "Partial answer", nudgeMessage.content)
        }
        assertEquals(
            listOf("Question", "Partial answer", nudgeMessage.content),
            relevant.map { it.content }
        )
        assertFalse(request.messages.any { it.content == "Write Char's next reply." })
        assertEquals(LLMMessageRole.User, nudgeMessage.role)
        assertEquals(nudgeMessage, request.messages.last())
    }

    @Test
    fun continueAlwaysEndsWithUserNudge() {
        val result = builder.buildWithMetadata(
            context(
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "Question"),
                    chatMessage(2L, ChatMessage.Source.Char, "Partial answer")
                ),
                generationMode = PromptGenerationMode.Continue
            )
        )
        val request = result.request

        assertEquals(LLMMessageRole.User, request.messages.last().role)
        assertTrue(request.messages.last().content.contains("Continue your last message"))
        assertTrue(
            result.inspection.items.last().sources.any {
                it.kind == PromptSourceKind.ContinueNudge
            }
        )
        assertEquals(
            "Partial answer",
            request.messages[request.messages.lastIndex - 1].content
        )
    }

    @Test
    fun singleUserModeKeepsContinueNudgeInFlattenedUserMessage() {
        val request = builder.build(
            context(
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "Question"),
                    chatMessage(2L, ChatMessage.Source.Char, "Partial answer")
                ),
                provider = provider(PromptPostProcessingMode.SingleUserMessage),
                generationMode = PromptGenerationMode.Continue
            )
        )

        assertEquals(1, request.messages.size)
        assertEquals(LLMMessageRole.User, request.messages.single().role)
        assertTrue(request.messages.single().content.contains("Continue your last message"))
    }

    @Test
    fun normalPostHistoryInstructionsUseTerminalUserRole() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "Keep the reply concise."
                ),
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "Question")
                )
            )
        )

        assertEquals("Keep the reply concise.", request.messages.last().content)
        assertEquals(LLMMessageRole.User, request.messages.last().role)
    }

    @Test
    fun normalGenerationAddsUserNudgeAfterDepthZeroSystemInjection() {
        val request = builder.build(
            context(
                messages = listOf(
                    chatMessage(1L, ChatMessage.Source.User, "Question")
                ),
                entries = listOf(
                    lorebookEntry(1L, 100, 0, "Depth zero rule")
                )
            )
        )

        assertEquals(LLMMessageRole.User, request.messages.last().role)
        assertEquals("[Write Char's next reply.]", request.messages.last().content)
    }

    @Test
    fun summaryDefaultsToImmediatelyAfterMainPrompt() {
        val result = builder.buildWithMetadata(context(summary = "Known events"))
        val kinds = result.inspection.items
            .flatMap { it.sources }
            .map { it.kind }
            .filter { it in setOf(PromptSourceKind.MainPrompt, PromptSourceKind.Summary) }

        assertEquals(
            listOf(PromptSourceKind.MainPrompt, PromptSourceKind.Summary),
            kinds
        )
    }

    @Test
    fun sameDepthAndRoleWorldInfoEntriesAreJoined() {
        val result = builder.buildWithMetadata(
            context(
                messages = listOf(chatMessage(1L, ChatMessage.Source.User, "Hello")),
                entries = listOf(
                    lorebookEntry(1L, 100, 0, "First depth lore"),
                    lorebookEntry(2L, 90, 0, "Second depth lore")
                )
            )
        )

        val item = result.inspection.items.first {
            it.content.contains("First depth lore") && it.content.contains("Second depth lore")
        }
        assertEquals(
            setOf(1L, 2L),
            item.sources.mapNotNull { it.referenceId }.toSet()
        )
    }

    @Test
    fun exampleDialogueUsesUserAndAssistantRoles() {
        val request = builder.build(
            context(
                character = Character(
                    id = 1L,
                    name = "Char",
                    avatar = "",
                    characterTags = "[]",
                    description = "",
                    personality = "",
                    scenario = "",
                    firstMessages = "",
                    examplesOfDialogue = "<START>\nUser: Hello\nChar: Welcome",
                    postHistoryInstructions = ""
                )
            )
        )

        assertEquals(
            LLMMessageRole.User,
            request.messages.first { it.content == "Hello" }.role
        )
        assertEquals(
            LLMMessageRole.Assistant,
            request.messages.first { it.content == "Welcome" }.role
        )
    }

    @Test
    fun chineseHistoryIsTrimmedByFinalTokenizerBudget() {
        val result = builder.buildWithMetadata(
            context(
                messages = listOf(
                    chatMessage(
                        id = 1L,
                        source = ChatMessage.Source.User,
                        content = "旧".repeat(300)
                    ),
                    chatMessage(
                        id = 2L,
                        source = ChatMessage.Source.Char,
                        content = "更早".repeat(200)
                    ),
                    chatMessage(
                        id = 3L,
                        source = ChatMessage.Source.User,
                        content = "最新消息"
                    )
                ),
                maxContextTokens = 500,
                maxResponseTokens = 100
            )
        )

        assertTrue(result.inspection.finalTokenCount <= 400)
        assertTrue(result.request.messages.any { it.content == "最新消息" })
        assertTrue(
            result.inspection.omittedItems.any {
                it.reason == PromptOmissionReason.ContextBudget
            }
        )
    }

    @Test
    fun expandedHistoryMacroCannotBypassFinalBudget() {
        val character = Character(
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
            systemPrompt = "{{history}}"
        )

        assertThrows(PromptBudgetExceededException::class.java) {
            builder.buildWithMetadata(
                context(
                    character = character,
                    messages = listOf(
                        chatMessage(
                            id = 1L,
                            source = ChatMessage.Source.User,
                            content = "历史".repeat(300)
                        ),
                        chatMessage(
                            id = 2L,
                            source = ChatMessage.Source.User,
                            content = "最新消息"
                        )
                    ),
                    maxContextTokens = 500,
                    maxResponseTokens = 100
                )
            )
        }
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
        entries: List<LorebookEntry> = emptyList(),
        userDescription: String = "",
        summary: String = "",
        generationMode: PromptGenerationMode = PromptGenerationMode.Normal,
        maxContextTokens: Int = 4096,
        maxResponseTokens: Int = 512,
        regexScripts: List<ScopedRegexScript> = emptyList(),
        provider: LLMProvider? = null
    ): PromptBuildContext {
        return PromptBuildContext(
            userName = "User",
            userDescription = userDescription,
            character = character,
            session = session,
            summary = summary,
            messages = messages,
            currentUserMessage = null,
            candidateLorebookEntries = entries,
            provider = provider,
            maxContextTokens = maxContextTokens,
            maxResponseTokens = maxResponseTokens,
            generationMode = generationMode,
            regexScripts = regexScripts
        )
    }

    private fun provider(postProcessingMode: PromptPostProcessingMode): LLMProvider {
        return LLMProvider(
            name = "Test",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.com",
            model = "test",
            promptPostProcessingMode = postProcessingMode.ordinal
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
            content = content
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
