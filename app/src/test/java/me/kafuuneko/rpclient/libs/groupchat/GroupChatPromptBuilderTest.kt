package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.TestPromptPreferences
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehavior
import me.kafuuneko.rpclient.libs.prompt.model.ExampleDialogueBehaviorProvider
import me.kafuuneko.rpclient.libs.prompt.model.PromptOmissionReason
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.prompt.model.PromptTokenizerStrategy
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.prompt.request.PromptRequestFinalizer
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizer
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
    fun classicGroupSummaryTrimsOldHistoryAndReplacesSpeakerTask() {
        val lyra = character(1, "Lyra")
        val builder = GroupChatPromptBuilder(
            TestPromptPreferences(
                mainPrompt = "GROUP_MAIN_CONTEXT",
                groupNudgePrompt = "CHARACTER_REPLY_TASK",
                summaryInjectionPosition = -1,
                includeThinkInContext = true,
            )
        )
        val old =
            message(GroupChatMessage.Source.User, "Alex", "OLD_EVENT ".repeat(400)).copy(id = 1)
        val recent = message(
            GroupChatMessage.Source.Character,
            "Lyra",
            "<think>PRIVATE_REASONING</think>LATEST_EVENT"
        ).copy(id = 2)
        val input = GroupChatPromptContext(
            session = GroupChatSession(
                id = 1,
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex",
                userDescription = ""
            ),
            members = listOf(member(lyra, 0)), speaker = lyra,
            messages = listOf(old, recent), provider = provider(),
            summary = "<think>PRIVATE_MEMORY</think>KNOWN_MEMORY",
            summaryInstruction = "SUMMARIZE_CURRENT_CONTEXT",
        )
        // 缩小输入预算后必须留下近期消息、角色背景和摘要任务。
        val minimum =
            builder.buildWithMetadata(input.copy(messages = listOf(recent))).inspection.finalTokenCount
        val result = builder.buildWithMetadata(
            input.copy(
                provider = provider(contextTokens = minimum + input.provider.maxTokens + 32),
            )
        )
        val text = result.request.messages.joinToString("\n") { it.content }
        assertTrue(text.contains("Lyra description"))
        assertTrue(text.contains("GROUP_MAIN_CONTEXT"))
        assertTrue(text.contains("KNOWN_MEMORY"))
        assertTrue(text.contains("LATEST_EVENT"))
        assertFalse(text.contains("OLD_EVENT"))
        assertFalse(text.contains("PRIVATE_"))
        assertFalse(text.contains("CHARACTER_REPLY_TASK"))
        assertEquals("SUMMARIZE_CURRENT_CONTEXT", result.request.messages.last().content)
        assertTrue(result.inspection.finalTokenCount <= result.inspection.promptBudget)
        assertTrue(result.inspection.omittedItems.any {
            it.source.referenceId == old.id && it.reason == PromptOmissionReason.ContextBudget
        })
    }

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
    fun sessionExampleSwitchRespectsCardModeAndPreservesWorldInfo() {
        val lyra = character(id = 1, name = "Lyra").copy(
            examplesOfDialogue = "<START>\nLyra: LYRA_EXAMPLE_STYLE",
        )
        val mira = character(id = 2, name = "Mira").copy(
            examplesOfDialogue = "<START>\nMira: MIRA_EXAMPLE_STYLE",
        )
        val builder = GroupChatPromptBuilder(
            mPreferences = TestPromptPreferences(),
            mExampleDialogueBehaviorProvider = ExampleDialogueBehaviorProvider {
                ExampleDialogueBehavior.Pinned
            },
        )
        val base = GroupChatPromptContext(
            session = GroupChatSession(
                title = "Crew", createTime = 1, latestTime = 1,
                userName = "Alex", userDescription = "",
            ),
            members = listOf(
                member(character = lyra, order = 0),
                member(character = mira, order = 1)
            ),
            speaker = lyra,
            messages = emptyList(),
            provider = provider(),
            candidateLorebookEntries = listOf(
                worldEntry(
                    id = 91L, order = 100, content = "WORLD_EXAMPLE_TOP",
                    position = LorebookEntry.POSITION_EXAMPLE_TOP,
                ),
                worldEntry(
                    id = 92L, order = 100, content = "WORLD_EXAMPLE_BOTTOM",
                    position = LorebookEntry.POSITION_EXAMPLE_BOTTOM,
                ),
            ),
        )
        // 全部生成模式共用会话开关，开启时仍按 Swap/Join 选择成员示例。
        GroupChatGenerationMode.entries.forEach { generationMode ->
            GroupChatSession.CharacterCardMode.entries.forEach { cardMode ->
                listOf(true, false).forEach { enabled ->
                    val request = builder.build(
                        context = base.copy(
                            session = base.session.copy(
                                characterCardMode = cardMode,
                                includeCharacterExamples = enabled,
                            ),
                            generationMode = generationMode,
                        ),
                    )
                    val content = request.messages.joinToString("\n") { it.content }
                    assertEquals(enabled, "LYRA_EXAMPLE_STYLE" in content)
                    assertEquals(
                        enabled && cardMode == GroupChatSession.CharacterCardMode.Join,
                        "MIRA_EXAMPLE_STYLE" in content,
                    )
                    assertTrue("WORLD_EXAMPLE_TOP" in content)
                    assertTrue("WORLD_EXAMPLE_BOTTOM" in content)
                }
            }
        }
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
                provider = provider(contextTokens = 550, maxTokens = 100),
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
        assertEquals(
            """
            Lyra:
            <description>
            Char Description (Lyra):
            Lyra description
            </description>
            Mina:
            <description>
            Char Description (Mina):
            Mina description
            </description>
            """.trimIndent(),
            descriptions,
        )
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
        // 旧预设没有群聊代写键时使用新的群聊默认词，不继承已有单聊覆盖项。
        val legacyPreset = PromptTemplateSnapshot.resolve(
            presetId = 7L,
            revision = 1L,
            overrides = mapOf(
                "impersonation" to "SINGLE_IMPERSONATION",
                "user_writing" to "SINGLE_WRITING",
            ),
        )
        val request = GroupChatPromptBuilder(mPreferences = legacyPreset).build(
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
        assertFalse(request.messages.any { "SINGLE_" in it.content })
        assertTrue(request.messages.last().content.contains("point of view of Alex"))
        assertTrue(request.messages.last().content.contains("Group members: Lyra"))
        assertEquals(LLMMessageRole.User, request.messages.last().role)
        val draftRequest = GroupChatPromptBuilder(mPreferences = legacyPreset).build(
            context = GroupChatPromptContext(
                session = GroupChatSession(
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex"
                ),
                members = listOf(member(lyra, 0)), speaker = lyra, messages = emptyList(),
                provider = provider(), generationMode = GroupChatGenerationMode.Impersonate,
                userWritingDraft = "unfinished",
            ),
        )
        assertTrue(draftRequest.messages.last().content.contains("[Group user writing task]"))
        assertFalse(draftRequest.messages.any { "SINGLE_" in it.content })
    }

    /** 群聊续写展开群成员宏，同时保留输入原文与独立推理通道。 */
    @Test
    fun userDraftIsLiteralAndOnlyDraftModeSeparatesReasoning() {
        val character = character(1, "Lyra")
        val muted = member(character(2, "Mira"), 1).let {
            it.copy(relation = it.relation.copy(muted = true))
        }
        val original = "  {{user}} <CHAR> {{draft}} {{groupNotMuted}}  "
        val builder = GroupChatPromptBuilder(
            TestPromptPreferences(
                impersonationPrompt = "SINGLE_IMPERSONATION",
                userWritingPrompt = "SINGLE_WRITING",
                groupImpersonationPrompt = "GROUP_BASE {{group}} / {{ GROUPNOTMUTED }} / {{user}}",
                groupUserWritingPrompt = "GROUP_DRAFT {{group}} / {{ GROUPNOTMUTED }} / {{user}} [{{ DRAFT }}]",
            )
        )
        val input = GroupChatPromptContext(
            session = GroupChatSession(
                id = 1,
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex"
            ),
            members = listOf(member(character, 0), muted),
            speaker = character,
            messages = emptyList(),
            provider = provider(),
            generationMode = GroupChatGenerationMode.Impersonate,
        )
        // 历史输出只读取群聊身份约束，输入框额外追加群聊续写任务，名单不受上下文角色影响。
        val history = builder.build(input)
        val draft = builder.buildWithMetadata(input.copy(userWritingDraft = original))
        assertTrue(history.includeReasoningInContent)
        assertFalse(draft.request.includeReasoningInContent)
        assertTrue(draft.request.captureReasoning)
        assertEquals("GROUP_BASE Lyra, Mira / Lyra / Alex", history.messages.last().content)
        assertFalse(history.messages.any { "GROUP_DRAFT" in it.content })
        assertEquals(
            "GROUP_DRAFT Lyra, Mira / Lyra / Alex [$original]",
            draft.request.messages.last().content
        )
        assertTrue(draft.request.messages.any { it.content == history.messages.last().content })
        assertEquals(
            history.messages.last(),
            builder.build(input.copy(speaker = muted.character)).messages.last()
        )
        val allMuted = input.copy(members = input.members.map { member ->
            member.copy(relation = member.relation.copy(muted = true))
        })
        assertEquals(
            "GROUP_BASE Lyra, Mira /  / Alex",
            builder.build(allMuted).messages.last().content
        )
        assertFalse(draft.request.messages.any { "SINGLE_" in it.content })
        assertFalse(history.messages.any { "SINGLE_" in it.content })
        assertTrue(draft.inspection.items.last().sources.any { it.kind == PromptSourceKind.ImpersonationNudge })
        // 空输入沿用群聊身份指令，不追加自定义续写词，也不能切回聊天记录的推理模式。
        listOf("", " \t\n").forEach { emptyDraft ->
            val emptyInput = builder.build(input.copy(userWritingDraft = emptyDraft))
            assertEquals(history.messages, emptyInput.messages)
            assertFalse(emptyInput.includeReasoningInContent)
            assertTrue(emptyInput.captureReasoning)
        }
    }

    /** 群聊自定义模板省略草稿宏时保留原文，显式清空和预算超限均不能静默回退。 */
    @Test
    fun groupWritingHonorsEmptyPresetAndProtectsUnsentMaterial() {
        val lyra = character(1, "Lyra")
        val input = GroupChatPromptContext(
            session = GroupChatSession(
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex"
            ),
            members = listOf(member(lyra, 0)), speaker = lyra, messages = emptyList(),
            provider = provider(), generationMode = GroupChatGenerationMode.Impersonate,
        )
        val original = "  {{group}} {{user}}  "
        val builder = GroupChatPromptBuilder(mPreferences = TestPromptPreferences())
        // 模板中的名字展开，但用户未发送文字中的字面宏不参与替换。
        val custom = PromptTemplateSnapshot.resolve(
            presetId = 8L,
            revision = 1L,
            overrides = mapOf(
                "group_impersonation" to "GROUP_BASE {{group}}",
                "group_user_writing" to "CUSTOM {{groupNotMuted}}",
            ),
        )
        val customRequest = builder.build(input.copy(userWritingDraft = original), custom)
        assertEquals("CUSTOM Lyra\n\n$original", customRequest.messages.last().content)
        val empty = PromptTemplateSnapshot.resolve(
            presetId = 8L,
            revision = 2L,
            overrides = mapOf("group_impersonation" to "", "group_user_writing" to ""),
        )
        val emptyRequest = builder.build(input.copy(userWritingDraft = original), empty)
        assertEquals(original, emptyRequest.messages.last().content)
        val clearedHistory = builder.buildWithMetadata(input, empty)
        assertFalse(clearedHistory.inspection.items.any { item ->
            item.sources.any { it.kind == PromptSourceKind.ImpersonationNudge }
        })
        assertFalse(emptyRequest.messages.any { "[Group user impersonation task]" in it.content })
        assertFalse(emptyRequest.messages.any { "[Group user writing task]" in it.content })
        // 必需草稿不能被预算裁剪成一次忽略原文的重新代写。
        assertThrows(PromptBudgetExceededException::class.java) {
            builder.build(
                context = input.copy(
                    provider = provider(contextTokens = 2000, maxTokens = 100),
                    userWritingDraft = "未".repeat(4000),
                ),
                preferences = custom,
            )
        }
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
                provider = provider(contextTokens = 360, maxTokens = 50),
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

    /** 角色卡格式、正文和深度注释都属于卡片作者，换人发言只改变本轮任务。 */
    @Test
    fun rendersEachCardBeforeJoiningAndKeepsExamplesWithTheirAuthor() {
        val alice = character(1, "Alice").copy(
            description = "{{ CHAR }} greets {{ USER }}; {{persona}}",
            personality = "{{char}} keeps notes",
            scenario = "<BOT> guards",
            depthPromptPrompt = "Remember {{char}}",
            examplesOfDialogue = "<START>\n{{ CHAR }}: Meet {{char}} and {{user}}\n<START>\nAbout <CHAR>",
        )
        val bob = character(2, "Bob").copy(personality = "{{char}} sings")
        val builder =
            GroupChatPromptBuilder(TestPromptPreferences(newExampleChatPrompt = "Example for {{char}}"))
        val context = GroupChatPromptContext(
            session = GroupChatSession(
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex",
                userDescription = "{{scenario}}",
                characterCardMode = GroupChatSession.CharacterCardMode.Join,
            ),
            members = listOf(member(alice, 0), member(bob, 1)),
            speaker = bob,
            messages = emptyList(),
            provider = provider(),
        )
        // 合并模式同时包含两张卡，检查器与最终请求中都应能看到各自的作者。
        for (speaker in listOf(alice, bob)) {
            val result = builder.buildWithMetadata(context.copy(speaker = speaker))
            assertEquals(
                result.request.messages.map { it.content },
                result.inspection.items.map { it.content })
            val content = result.request.messages.joinToString("\n") { it.content }
            for (fragment in listOf(
                "Alice:\n<description>\nChar Description (Alice):\nAlice greets Alex; Alice guards",
                "Char Personality (Alice):",
                "Alice keeps notes",
                "Char Personality (Bob):",
                "Bob sings",
                "Char Scenario (Alice):",
                "Alice guards",
                "Remember Alice",
                "Example for Alice",
                "Alice: Meet Alice and Alex",
                "About Alice"
            )) {
                assertTrue(fragment, fragment in content)
            }
        }
        // 切换模式只注入当前角色；缺少群场景覆盖时预设仍能引用角色卡场景。
        val swap = context.copy(
            session = context.session.copy(characterCardMode = GroupChatSession.CharacterCardMode.Swap),
            speaker = alice,
        )
        val swapRequest = builder.build(
            swap,
            TestPromptPreferences(
                mainPrompt = "{{scenario}} / {{charIfNotGroup}}END",
                descriptionFormat = "Profile of {{char}}: {{description}}",
            )
        )
        assertTrue(swapRequest.messages.any { it.content == "Alice guards / END" })
        assertTrue(swapRequest.messages.any { it.content == "Profile of Alice: Alice greets Alex; Alice guards" })
        assertFalse(swapRequest.messages.any { "Bob sings" in it.content || "Bob description" in it.content })
        // 用户和角色同名时，示例的角色标签仍应生成 assistant 消息。
        val sameName = swap.copy(persona = swap.persona.copy(name = alice.name))
        assertTrue(builder.build(sameName).messages.any {
            it.role == LLMMessageRole.Assistant && it.content == "Alice: Meet Alice and Alice"
        })
    }

    /** 世界书匹配和注入复用卡片归属；只有最终进入请求的插槽才推进时序。 */
    @Test
    fun scansResolvedCardsAndTracksOnlyRetainedOutletSources() {
        val alice = character(1, "Alice").copy(
            personality = "{{char}} trusts {{user}}",
            characterLorebookId = 1
        )
        val bob = character(2, "Bob")
        val outlet = worldEntry(91, 100, "{{char}} knows {{user}}", LorebookEntry.POSITION_OUTLET)
            .copy(
                constant = false,
                keywords = "[\"Alice trusts Alex\"]",
                matchCharacterPersonality = true,
                outletName = "archive",
                sticky = 3,
            )
        val builder =
            GroupChatPromptBuilder(TestPromptPreferences(mainPrompt = "Memory: {{outlet::archive}}"))
        val base = GroupChatPromptContext(
            session = GroupChatSession(
                title = "Crew",
                createTime = 1,
                latestTime = 1,
                userName = "Alex",
                characterCardMode = GroupChatSession.CharacterCardMode.Join,
            ),
            members = listOf(member(alice, 0), member(bob, 1)),
            speaker = bob,
            messages = emptyList(),
            provider = provider(),
            candidateLorebookEntries = listOf(outlet),
        )
        // 条目和引用模板属于不同角色；裁剪后仍能从最终来源确认实际使用的条目。
        val result = builder.buildWithMetadata(base)
        assertTrue(result.request.messages.any { it.content == "Memory: Alice knows Alex" })
        assertTrue(result.inspection.items.any { item -> item.sources.any { it.referenceId == outlet.id } })
        assertTrue("\"91\"" in result.worldInfoStateJson)
        val unused =
            builder.buildWithMetadata(base, TestPromptPreferences(mainPrompt = "No outlet"))
        assertFalse("\"91\"" in unused.worldInfoStateJson)
    }

    /** 递归匹配使用实际注入的角色名，切换发言者后仍沿用原始条目的时序签名。 */
    @Test
    fun recursiveWorldInfoUsesResolvedTextAndPreservesCooldownAcrossTurns() {
        val alice = character(1, "Alice")
        val bob = character(2, "Bob")
        val trigger = worldEntry(11, 100, "{{char}} wakes", LorebookEntry.POSITION_BEFORE)
        val event = worldEntry(12, 100, "Echo for {{char}}", LorebookEntry.POSITION_AT_DEPTH).copy(
            constant = false,
            keywords = "[\"Bob wakes\"]",
            delayUntilRecursion = true,
            cooldown = 3,
        )
        val builder = GroupChatPromptBuilder(TestPromptPreferences())
        val base = GroupChatPromptContext(
            session = GroupChatSession(title = "Crew", createTime = 1, latestTime = 1),
            members = listOf(member(alice, 0), member(bob, 1)),
            speaker = bob,
            messages = emptyList(),
            provider = provider(),
            candidateLorebookEntries = listOf(trigger, event),
            recursiveScanningLorebookIds = setOf(1L),
        )
        // 共享世界书跟随本轮发言者。换人后仍能匹配同一关键词，用冷却验证状态签名。
        val first = builder.buildWithMetadata(base)
        assertTrue(first.request.messages.any { it.content == "Echo for Bob" })
        val next = base.copy(
            speaker = alice,
            totalMessageCount = 1,
            session = base.session.copy(worldInfoStateJson = first.worldInfoStateJson),
            candidateLorebookEntries = listOf(trigger.copy(content = "Bob wakes"), event),
        )
        assertFalse(builder.build(next).messages.any { it.content == "Echo for Alice" })
        assertTrue(builder.build(next.copy(totalMessageCount = 3)).messages.any { it.content == "Echo for Alice" })
    }

    /** 群聊构建器必须把重放标记传给共享激活器，并在预算后保留正确的冷却状态。 */
    @Test
    fun regenerationAndPartialRetryReplayWorldInfoAtTheSameMessageCount() {
        val alice = character(1, "Alice")
        val entry = worldEntry(21, 100, "TURN_LORE", LorebookEntry.POSITION_BEFORE).copy(
            cooldown = 3,
        )
        val builder = GroupChatPromptBuilder(TestPromptPreferences())
        val base = GroupChatPromptContext(
            session = GroupChatSession(title = "Crew", createTime = 1, latestTime = 1),
            members = listOf(member(alice, 0)), speaker = alice,
            messages = emptyList(), totalMessageCount = 20, provider = provider(),
            candidateLorebookEntries = listOf(entry),
        )
        val first = builder.buildWithMetadata(base)
        val persisted =
            base.copy(session = base.session.copy(worldInfoStateJson = first.worldInfoStateJson))
        assertFalse(builder.build(persisted).messages.any { it.content == "TURN_LORE" })

        // 显式重生成与 Normal 模式的残缺回复重试使用相同的轮次，但保留不同生成类型。
        for (mode in listOf(GroupChatGenerationMode.Regenerate, GroupChatGenerationMode.Normal)) {
            val replay = builder.buildWithMetadata(
                persisted.copy(
                    generationMode = mode,
                    isReplayingTurn = true
                )
            )
            assertTrue(replay.request.messages.any { it.content == "TURN_LORE" })
            assertEquals(first.worldInfoStateJson, replay.worldInfoStateJson)
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
