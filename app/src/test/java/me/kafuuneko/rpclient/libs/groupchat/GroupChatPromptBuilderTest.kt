package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizer
import me.kafuuneko.rpclient.libs.prompt.TestPromptPreferences
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatPromptBuilderTest {
    @Test
    fun userPersonaUsesSharedFormatAndCurrentSpeakerMacros() {
        val lyra = character(id = 1, name = "Lyra")
        val request = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(userPersonaFormat = "Profile of {{user}}: {{persona}}"),
        ).build(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "{{user}} trusts {{char}}.",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = emptyList(),
                provider = provider(),
            ),
        )

        assertTrue(request.messages.any { it.content == "Profile of Alex: Alex trusts Lyra." })
    }

    @Test
    fun disabledExampleBehaviorOmitsAllMemberExamples() {
        val lyra = character(id = 1, name = "Lyra").copy(
            examplesOfDialogue = "<START>\nUser: Example question\nLyra: Example answer",
        )
        val request = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mExampleDialogueBehaviorProvider = ExampleDialogueBehaviorProvider {
                ExampleDialogueBehavior.Disabled
            },
        ).build(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(message(GroupChatMessage.Source.User, "Alex", "Actual question")),
                provider = provider(),
            ),
        )

        assertFalse(request.messages.any { it.content.contains("Example question") })
        assertFalse(request.messages.any { it.content.contains("Example answer") })
        assertTrue(request.messages.any { it.content.contains("Actual question") })
    }

    @Test
    fun disabledWorldInfoExamplesDoNotConsumeGroupBudgetOrAdvanceState() {
        val lyra = character(id = 1, name = "Lyra")
        val example = worldEntry(
            id = 91L,
            order = 100,
            content = "EXAMPLE_" + "e".repeat(72),
            position = LorebookEntry.POSITION_EXAMPLE_TOP,
        ).copy(sticky = 2)
        val normal = worldEntry(
            id = 92L,
            order = 10,
            content = "NORMAL_" + "n".repeat(43),
            position = LorebookEntry.POSITION_BEFORE,
        )
        var providerReads = 0
        val tokenizer = object : PromptTokenizer {
            override val name = "Character count"
            override val strategy = PromptTokenizerStrategy.ModelAware
            override fun countText(text: String): Int = text.length
        }
        val result = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mRequestFinalizer = PromptRequestFinalizer { tokenizer },
            mExampleDialogueBehaviorProvider = ExampleDialogueBehaviorProvider {
                providerReads += 1
                ExampleDialogueBehavior.Disabled
            },
        ).buildWithMetadata(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(message(GroupChatMessage.Source.User, "Alex", "Actual")),
                provider = provider(contextTokens = 500, maxTokens = 100),
                candidateLorebookEntries = listOf(example, normal),
            ),
        )

        assertEquals(1, providerReads)
        assertFalse(result.request.messages.any { it.content.contains("EXAMPLE_") })
        assertTrue(result.request.messages.any { it.content.contains("NORMAL_") })
        assertFalse(result.inspection.omittedItems.any { it.source.referenceId == normal.id })
        assertFalse(result.worldInfoStateJson.contains("\"${example.id}\""))
    }

    @Test
    fun groupPromptUsesSamePromptOnlyRegexPipeline() {
        val lyra = character(id = 1, name = "Lyra")
        val result =
            GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).buildWithMetadata(
                context = GroupChatPromptContext(
                    session = GroupChatSession(
                        id = 1,
                        title = "Crew",
                        createTime = 1,
                        latestTime = 1,
                        userName = "Alex",
                        userDescription = "",
                    ),
                    members = listOf(member(character = lyra, order = 0)),
                    speaker = lyra,
                    messages = listOf(
                        message(
                            GroupChatMessage.Source.Character,
                            "Lyra",
                            "secret answer"
                        )
                    ),
                    provider = provider(),
                    regexScripts = listOf(
                        element = ScopedRegexScript(
                            script = RegexScript(
                                id = "reasoning",
                                scriptName = "AI rewrite",
                                findRegex = "/secret/g",
                                replaceString = "hidden",
                                placement = listOf(RegexPlacement.AiResponse.value),
                                promptOnly = true,
                            ),
                            scope = RegexScriptScope.Global,
                        ),
                    ),
                ),
            )

        assertTrue(result.request.messages.any { it.content.contains("hidden answer") })
        assertTrue(result.inspection.regexExecutions.any { it.scriptId == "reasoning" })
    }

    @Test
    fun promptKeepsHistoricalSpeakersAndEndsHistoryWithGroupNudge() {
        val lyra = character(id = 1, name = "Lyra")
        val mina = character(id = 2, name = "Mina")
        val session = GroupChatSession(
            id = 1,
            title = "Crew",
            createTime = 1,
            latestTime = 1,
            userName = "Alex",
            userDescription = "",
        )
        val request = GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(
            context = GroupChatPromptContext(
                session = session,
                members = listOf(
                    member(character = lyra, order = 0),
                    member(character = mina, order = 1)
                ),
                speaker = mina,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Look outside."),
                    message(GroupChatMessage.Source.Character, "Lyra", "I see a station."),
                ),
                provider = provider(),
            ),
        )

        val content = request.messages.joinToString("\n") { it.content }
        assertFalse(content.contains("Current responding character:"))
        assertFalse(content.contains("Group members:"))
        assertTrue(content.contains("Mina description"))
        assertTrue(content.contains("Alex: Look outside."))
        assertTrue(content.contains("Lyra: I see a station."))
        assertTrue(content.contains("Write only Mina's next reply"))
        assertEquals(
            LLMMessageRole.User,
            request.messages.first { it.content.contains("Write only Mina's next reply") }.role,
        )
    }

    @Test
    fun joinedCardsAreGroupedByFieldAndDepthPromptsStayInChat() {
        val lyra = character(id = 1, name = "Lyra").copy(
            personality = "Calm",
            scenario = "Bridge",
            depthPromptPrompt = "Lyra depth",
            depthPromptDepth = 0,
            depthPromptRole = LorebookEntry.ROLE_ASSISTANT,
        )
        val mina = character(id = 2, name = "Mina").copy(
            personality = "Bold",
            scenario = "Dock",
            depthPromptPrompt = "Mina depth",
            depthPromptDepth = 1,
        )
        val result =
            GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).buildWithMetadata(
                context = GroupChatPromptContext(
                    session = GroupChatSession(
                        id = 1,
                        title = "Crew",
                        createTime = 1,
                        latestTime = 1,
                        userName = "Alex",
                        userDescription = "",
                        characterCardMode = GroupChatSession.CharacterCardMode.Join,
                    ),
                    members = listOf(
                        member(character = lyra, order = 0),
                        member(character = mina, order = 1)
                    ),
                    speaker = mina,
                    messages = listOf(message(GroupChatMessage.Source.User, "Alex", "Ready?")),
                    provider = provider(),
                ),
            )

        val descriptions = result.inspection.items.first { promptInspectionItem ->
            promptInspectionItem.sources.any { source -> source.kind == PromptSourceKind.CharacterDescription }
        }.content
        assertTrue(descriptions.contains("Lyra:\nLyra description"))
        assertTrue(descriptions.contains("Mina:\nMina description"))
        assertEquals(
            LLMMessageRole.Assistant,
            result.request.messages.first { it.content == "Lyra depth" }.role,
        )
        assertTrue(result.request.messages.any { it.content == "Mina depth" })
    }

    @Test
    fun continueFallbackOmitsCharacterReplyTasksAndEndsWithUserControl() {
        val lyra = character(id = 1, name = "Lyra").copy(
            postHistoryInstructions = "Group PHI",
            systemPrompt = "Write Lyra's next reply.",
            depthPromptPrompt = "Always write as Lyra.",
            depthPromptDepth = 0,
        )
        val request = GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Question"),
                    message(GroupChatMessage.Source.Character, "Lyra", "Partial"),
                ),
                provider = provider(),
                generationMode = GroupChatGenerationMode.Continue,
            ),
        )

        val continueNudge =
            request.messages.first { it.content.contains("Continue your last message") }
        val relevant = request.messages.filter { message ->
            message.content.contains("Write only Lyra")
                || message.content == "Group PHI"
                || message.content == "Write Lyra's next reply."
                || message.content == "Lyra: Partial"
                || message === continueNudge
        }
        assertEquals(
            listOf(
                "Lyra: Partial",
                continueNudge.content,
            ),
            relevant.map { it.content },
        )
        assertEquals(LLMMessageRole.User, continueNudge.role)
        assertEquals(continueNudge, request.messages.last())
    }

    @Test
    fun impersonateOmitsGroupNudgeAndPlacesControlPromptLast() {
        val lyra = character(id = 1, name = "Lyra").copy(
            postHistoryInstructions = "Group PHI",
            systemPrompt = "Write Lyra's next reply.",
        )
        val request = GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(message(GroupChatMessage.Source.Character, "Lyra", "Your turn.")),
                provider = provider(),
                generationMode = GroupChatGenerationMode.Impersonate,
            ),
        )

        assertFalse(request.messages.any { it.content.contains("Write only Lyra") })
        assertFalse(request.messages.any { it.content == "Write Lyra's next reply." })
        assertFalse(request.messages.any { it.content == "Group PHI" })
        assertFalse(request.messages.any { it.content == "Always write as Lyra." })
        assertTrue(request.messages.last().content.contains("point of view of Alex"))
        assertEquals(LLMMessageRole.User, request.messages.last().role)
    }

    @Test
    fun groupContinueAlwaysEndsWithUserNudge() {
        val lyra = character(id = 1, name = "Lyra")
        val result =
            GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).buildWithMetadata(
                context = GroupChatPromptContext(
                    session = GroupChatSession(
                        id = 1,
                        title = "Crew",
                        createTime = 1,
                        latestTime = 1,
                        userName = "Alex",
                        userDescription = "",
                    ),
                    members = listOf(member(character = lyra, order = 0)),
                    speaker = lyra,
                    messages = listOf(
                        message(GroupChatMessage.Source.User, "Alex", "Question"),
                        message(GroupChatMessage.Source.Character, "Lyra", "Partial"),
                    ),
                    provider = provider(),
                    generationMode = GroupChatGenerationMode.Continue,
                ),
            )
        val request = result.request

        assertTrue(request.messages.last().content.contains("Continue your last message"))
        assertTrue(result.inspection.items.last().sources.any { it.kind == PromptSourceKind.ContinueNudge })
        assertFalse(request.messages.any { it.content.contains("Write only Lyra") })
        assertEquals(LLMMessageRole.User, request.messages.last().role)
        assertEquals(
            "Lyra: Partial",
            request.messages[request.messages.lastIndex - 1].content,
        )
    }

    @Test
    fun finalGroupPromptStaysWithinBudgetAndExplainsRemovedHistory() {
        val lyra = character(id = 1, name = "Lyra")
        val mina = character(id = 2, name = "Mina")
        val result =
            GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).buildWithMetadata(
                context = GroupChatPromptContext(
                    session = GroupChatSession(
                        id = 1,
                        title = "Crew",
                        createTime = 1,
                        latestTime = 1,
                        userName = "Alex",
                        userDescription = "",
                    ),
                    members = listOf(
                        member(character = lyra, order = 0),
                        member(character = mina, order = 1)
                    ),
                    speaker = mina,
                    messages = listOf(
                        message(GroupChatMessage.Source.User, "Alex", "旧".repeat(300)),
                        message(GroupChatMessage.Source.Character, "Lyra", "早".repeat(300)),
                        message(GroupChatMessage.Source.User, "Alex", "Latest"),
                    ),
                    provider = provider(contextTokens = 1_000, maxTokens = 100),
                ),
            )

        assertTrue(result.inspection.finalTokenCount <= 900)
        assertTrue(result.request.messages.any { it.content.contains("Alex: Latest") })
        assertTrue(result.inspection.omittedItems.isNotEmpty())
    }

    @Test
    fun selectedGroupWorldInfoIsReservedBeforeOlderHistory() {
        val lyra = character(id = 1, name = "Lyra")
        val worldContent = "WORLD_" + "w".repeat(94)
        val tokenizer = object : PromptTokenizer {
            override val name = "Character count"
            override val strategy = PromptTokenizerStrategy.ModelAware
            override fun countText(text: String): Int = text.length
        }
        val result = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mRequestFinalizer = PromptRequestFinalizer { tokenizer },
        ).buildWithMetadata(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(
                    message(
                        source = GroupChatMessage.Source.Character,
                        speaker = "Lyra",
                        content = "OLD_HISTORY_" + "o".repeat(388),
                    ),
                    message(GroupChatMessage.Source.User, "Alex", "LATEST"),
                ),
                provider = provider(contextTokens = 700, maxTokens = 100),
                candidateLorebookEntries = listOf(
                    element = worldEntry(
                        id = 93L,
                        order = 100,
                        content = worldContent,
                        position = LorebookEntry.POSITION_BEFORE,
                    ),
                ),
            ),
        )

        assertTrue(result.request.messages.any { it.content.contains(worldContent) })
        assertTrue(result.request.messages.any { it.content.contains("LATEST") })
        assertFalse(result.request.messages.any { it.content.contains("OLD_HISTORY_") })
        assertTrue(
            result.inspection.omittedItems.any { omittedItem ->
                omittedItem.source.kind == PromptSourceKind.ChatHistory
                    && omittedItem.reason == PromptOmissionReason.ContextBudget
            },
        )
    }

    @Test
    fun regularGroupWorldInfoCanBeDroppedWhenRequiredPromptStillOverflows() {
        val lyra = character(id = 1, name = "Lyra").copy(description = "d".repeat(40))
        val worldContent = "WORLD_" + "w".repeat(54)
        val tokenizer = object : PromptTokenizer {
            override val name = "Character count"
            override val strategy = PromptTokenizerStrategy.ModelAware
            override fun countText(text: String): Int = text.length
        }
        val result = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mRequestFinalizer = PromptRequestFinalizer { tokenizer },
        ).buildWithMetadata(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                    systemPromptOverride = "R",
                ),
                members = listOf(member(character = lyra, order = 0)),
                speaker = lyra,
                messages = listOf(message(GroupChatMessage.Source.User, "Alex", "LATEST")),
                provider = provider(contextTokens = 310, maxTokens = 50),
                candidateLorebookEntries = listOf(
                    element = worldEntry(
                        id = 95L,
                        order = 100,
                        content = worldContent,
                        position = LorebookEntry.POSITION_BEFORE,
                    ),
                ),
            ),
        )

        assertTrue(result.request.messages.any { it.content.contains("LATEST") })
        assertFalse(result.request.messages.any { it.content.contains(worldContent) })
        assertTrue(
            result.inspection.omittedItems.any { omittedItem ->
                omittedItem.source.referenceId == 95L
                    && omittedItem.reason == PromptOmissionReason.ContextBudget
            },
        )
    }

    @Test
    fun oversizedIgnoredBudgetGroupWorldInfoIsNotSilentlyTrimmed() {
        val lyra = character(id = 1, name = "Lyra")
        val worldContent = "OVERSIZED_WORLD_" + "w".repeat(800)
        val tokenizer = object : PromptTokenizer {
            override val name = "Character count"
            override val strategy = PromptTokenizerStrategy.ModelAware
            override fun countText(text: String): Int = text.length
        }
        assertThrows(PromptBudgetExceededException::class.java) {
            GroupChatPromptBuilder(
                mPreferences = TestPromptPreferences(),
                mRequestFinalizer = PromptRequestFinalizer { tokenizer },
            ).buildWithMetadata(
                context = GroupChatPromptContext(
                    session = GroupChatSession(
                        id = 1,
                        title = "Crew",
                        createTime = 1,
                        latestTime = 1,
                        userName = "Alex",
                        userDescription = "",
                    ),
                    members = listOf(member(character = lyra, order = 0)),
                    speaker = lyra,
                    messages = listOf(message(GroupChatMessage.Source.User, "Alex", "LATEST")),
                    provider = provider(contextTokens = 700, maxTokens = 100),
                    candidateLorebookEntries = listOf(
                        element = worldEntry(
                            id = 94L,
                            order = 100,
                            content = worldContent,
                            position = LorebookEntry.POSITION_BEFORE,
                        ).copy(ignoreBudget = true),
                    ),
                ),
            )
        }
    }

    @Test
    fun regenerateGenerationModeActivatesMatchingWorldInfoTrigger() {
        val lyra = character(id = 1, name = "Lyra")
        val session = GroupChatSession(
            id = 1,
            title = "Crew",
            createTime = 1,
            latestTime = 1,
            userName = "Alex",
            userDescription = "",
        )
        val entry = LorebookEntry(
            id = 1,
            lorebookId = 1,
            name = "Regenerate entry",
            keywords = """["station"]""",
            secondaryKeywords = "[]",
            constant = false,
            order = 100,
            depth = 0,
            category = "[]",
            content = "Regenerate-only lore",
            position = LorebookEntry.POSITION_BEFORE,
            triggers = """["regenerate"]""",
        )
        val baseContext = GroupChatPromptContext(
            session = session,
            members = listOf(member(character = lyra, order = 0)),
            speaker = lyra,
            messages = listOf(
                message(
                    GroupChatMessage.Source.User,
                    "Alex",
                    "Approach the station."
                )
            ),
            provider = provider(),
            candidateLorebookEntries = listOf(entry),
        )

        val normal =
            GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(baseContext)
        val regenerated = GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(
            context = baseContext.copy(generationMode = GroupChatGenerationMode.Regenerate),
        )

        assertFalse(normal.messages.any { it.content.contains(entry.content) })
        assertTrue(regenerated.messages.any { it.content.contains(entry.content) })
    }

    @Test
    fun groupMessageActivatesUnsetWholeWordCjkWorldInfo() {
        val lyra = character(id = 1, name = "Lyra")
        val entry = LorebookEntry(
            id = 2,
            lorebookId = 1,
            name = "School",
            keywords = """["学校"]""",
            secondaryKeywords = "[]",
            constant = false,
            order = 100,
            depth = 0,
            category = "[]",
            content = "School lore",
            position = LorebookEntry.POSITION_BEFORE,
            matchWholeWords = null,
        )
        val context = GroupChatPromptContext(
            session = GroupChatSession(
                id = 1,
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex",
                userDescription = "",
            ),
            members = listOf(member(character = lyra, order = 0)),
            speaker = lyra,
            messages = listOf(message(GroupChatMessage.Source.User, "Alex", "我们回学校吧。")),
            provider = provider(),
            candidateLorebookEntries = listOf(entry),
        )

        val request = GroupChatPromptBuilder(mPreferences = TestPromptPreferences()).build(context)

        assertTrue(request.messages.any { it.content.contains(entry.content) })
    }

    /** 当前批次保护触发图文，后续无新输入批次必须允许按预算整条裁剪。 */
    @Test
    fun onlyExplicitBatchTriggerProtectsOldUserImagesFromBudgetTrimming() {
        val lyra = character(id = 1, name = "Lyra")
        val image = LLMImageReference(
            uuid = "image",
            mimeType = "image/png",
            width = 640,
            height = 480,
            byteCount = 1000,
        )
        val userMessage =
            message(GroupChatMessage.Source.User, "Alex", "Compare this image").copy(id = 1)
        val reply =
            message(GroupChatMessage.Source.Character, "Lyra", "A blue square.").copy(id = 2)
        val tokenizer = object : PromptTokenizer {
            override val name = "test"
            override val strategy = PromptTokenizerStrategy.Estimated
            override fun countText(text: String) = text.length
        }
        val builder = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mRequestFinalizer = PromptRequestFinalizer { tokenizer },
        )
        val context = GroupChatPromptContext(
            session = GroupChatSession(
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex",
                userDescription = "",
            ),
            members = listOf(member(character = lyra, order = 0)),
            speaker = lyra,
            messages = listOf(userMessage, reply),
            messageImages = mapOf(userMessage.id to listOf(image)),
            provider = provider(contextTokens = 100_000),
        )
        // 足额预算保留旧图；紧预算仍足够容纳必需文字，唯一需要裁剪的是旧用户图文。
        assertEquals(listOf(image), builder.build(context).messages.flatMap { it.images })
        val requiredTextTokens = builder.buildWithMetadata(
            context = context.copy(messages = listOf(reply), messageImages = emptyMap()),
        ).inspection.finalTokenCount
        val limited =
            context.copy(provider = provider(contextTokens = requiredTextTokens + 512 + 32))
        val result = builder.buildWithMetadata(limited)
        assertTrue(result.request.messages.all { it.images.isEmpty() })
        assertFalse(result.request.messages.any { userMessage.content in it.content })
        assertTrue(
            result.inspection.omittedItems.any { omittedItem ->
                omittedItem.source.kind == PromptSourceKind.ChatHistory && omittedItem.source.referenceId == userMessage.id
                    && omittedItem.reason == PromptOmissionReason.ContextBudget
            },
        )
        // 同样的历史仍属于当前触发批次时，宁可报告预算不足，也不能静默删除用户图片。
        assertThrows(PromptBudgetExceededException::class.java) {
            builder.build(limited.copy(protectedUserMessageId = userMessage.id))
        }
    }

    private fun character(id: Long, name: String): Character {
        return Character(
            id = id,
            name = name,
            avatar = "",
            characterTags = "[]",
            description = "$name description",
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = "",
        )
    }

    private fun member(character: Character, order: Int): GroupChatMemberData {
        return GroupChatMemberData(
            relation = GroupChatMember(
                sessionId = 1,
                characterId = character.id,
                sortOrder = order
            ),
            character = character,
        )
    }

    private fun worldEntry(
        id: Long,
        order: Int,
        content: String,
        position: Int,
    ): LorebookEntry {
        return LorebookEntry(
            id = id,
            lorebookId = 1L,
            name = "Entry $id",
            keywords = "[]",
            secondaryKeywords = "[]",
            constant = true,
            order = order,
            depth = 0,
            category = "[]",
            content = content,
            position = position,
        )
    }

    private fun message(
        source: GroupChatMessage.Source,
        speaker: String,
        content: String,
    ): GroupChatMessage {
        return GroupChatMessage(
            sessionId = 1,
            createTime = 1,
            source = source,
            content = content,
            speakerCharacterId = null,
            speakerNameSnapshot = speaker,
        )
    }

    private fun provider(
        contextTokens: Int = 8192,
        maxTokens: Int = 512,
    ): LLMProvider {
        return LLMProvider(
            name = "Test",
            providerType = LLMProviderType.Custom,
            protocol = LLMProviderProtocol.OpenAICompatible,
            baseUrl = "https://example.com",
            model = "test",
            contextTokens = contextTokens,
            maxTokens = maxTokens,
            createTime = 1,
            updateTime = 1,
            isEnabled = true,
        )
    }
}
