package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.prompt.PromptSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatPromptBuilderTest {
    @Test
    fun groupPromptUsesSamePromptOnlyRegexPipeline() {
        val lyra = character(1, "Lyra")
        val result = GroupChatPromptBuilder().buildWithMetadata(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = ""
                ),
                members = listOf(member(lyra, 0)),
                speaker = lyra,
                messages = listOf(
                    message(GroupChatMessage.Source.Character, "Lyra", "secret answer")
                ),
                provider = provider(),
                regexScripts = listOf(
                    ScopedRegexScript(
                        RegexScript(
                            id = "reasoning",
                            scriptName = "AI rewrite",
                            findRegex = "/secret/g",
                            replaceString = "hidden",
                            placement = listOf(RegexPlacement.AiResponse.value),
                            promptOnly = true
                        ),
                        RegexScriptScope.Global
                    )
                )
            )
        )

        assertTrue(result.request.messages.any { it.content.contains("hidden answer") })
        assertTrue(result.inspection.regexExecutions.any { it.scriptId == "reasoning" })
    }

    @Test
    fun promptKeepsHistoricalSpeakersAndEndsHistoryWithGroupNudge() {
        val lyra = character(1, "Lyra")
        val mina = character(2, "Mina")
        val session = GroupChatSession(
            id = 1,
            title = "Crew",
            createTime = 1,
            latestTime = 1,
            userName = "Alex",
            userDescription = ""
        )
        val request = GroupChatPromptBuilder().build(
            GroupChatPromptContext(
                session = session,
                members = listOf(member(lyra, 0), member(mina, 1)),
                speaker = mina,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Look outside."),
                    message(GroupChatMessage.Source.Character, "Lyra", "I see a station.")
                ),
                provider = provider()
            )
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
            request.messages.first { it.content.contains("Write only Mina's next reply") }.role
        )
    }

    @Test
    fun joinedCardsAreGroupedByFieldAndDepthPromptsStayInChat() {
        val lyra = character(1, "Lyra").copy(
            personality = "Calm",
            scenario = "Bridge",
            depthPromptPrompt = "Lyra depth",
            depthPromptDepth = 0,
            depthPromptRole = LorebookEntry.ROLE_ASSISTANT
        )
        val mina = character(2, "Mina").copy(
            personality = "Bold",
            scenario = "Dock",
            depthPromptPrompt = "Mina depth",
            depthPromptDepth = 1
        )
        val result = GroupChatPromptBuilder().buildWithMetadata(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = "",
                    characterCardMode = GroupChatSession.CharacterCardMode.Join
                ),
                members = listOf(member(lyra, 0), member(mina, 1)),
                speaker = mina,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Ready?")
                ),
                provider = provider()
            )
        )

        val descriptions = result.inspection.items.first {
            it.sources.any { source -> source.kind == PromptSourceKind.CharacterDescription }
        }.content
        assertTrue(descriptions.contains("Lyra:\nLyra description"))
        assertTrue(descriptions.contains("Mina:\nMina description"))
        assertEquals(
            LLMMessageRole.Assistant,
            result.request.messages.first { it.content == "Lyra depth" }.role
        )
        assertTrue(result.request.messages.any { it.content == "Mina depth" })
    }

    @Test
    fun continueFallbackOmitsCharacterReplyTasksAndEndsWithUserControl() {
        val lyra = character(1, "Lyra").copy(
            postHistoryInstructions = "Group PHI",
            systemPrompt = "Write Lyra's next reply.",
            depthPromptPrompt = "Always write as Lyra.",
            depthPromptDepth = 0
        )
        val request = GroupChatPromptBuilder().build(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = ""
                ),
                members = listOf(member(lyra, 0)),
                speaker = lyra,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Question"),
                    message(GroupChatMessage.Source.Character, "Lyra", "Partial")
                ),
                provider = provider(),
                generationMode = GroupChatGenerationMode.Continue
            )
        )

        val continueNudge = request.messages.first {
            it.content.contains("Continue your last message")
        }
        val relevant = request.messages.filter {
            it.content.contains("Write only Lyra") ||
                it.content == "Group PHI" ||
                it.content == "Write Lyra's next reply." ||
                it.content == "Lyra: Partial" ||
                it === continueNudge
        }
        assertEquals(
            listOf(
                "Lyra: Partial",
                continueNudge.content
            ),
            relevant.map { it.content }
        )
        assertEquals(LLMMessageRole.User, continueNudge.role)
        assertEquals(continueNudge, request.messages.last())
    }

    @Test
    fun impersonateOmitsGroupNudgeAndPlacesControlPromptLast() {
        val lyra = character(1, "Lyra").copy(
            postHistoryInstructions = "Group PHI",
            systemPrompt = "Write Lyra's next reply."
        )
        val request = GroupChatPromptBuilder().build(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = ""
                ),
                members = listOf(member(lyra, 0)),
                speaker = lyra,
                messages = listOf(
                    message(GroupChatMessage.Source.Character, "Lyra", "Your turn.")
                ),
                provider = provider(),
                generationMode = GroupChatGenerationMode.Impersonate
            )
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
        val lyra = character(1, "Lyra")
        val result = GroupChatPromptBuilder().buildWithMetadata(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = ""
                ),
                members = listOf(member(lyra, 0)),
                speaker = lyra,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "Question"),
                    message(GroupChatMessage.Source.Character, "Lyra", "Partial")
                ),
                provider = provider(),
                generationMode = GroupChatGenerationMode.Continue
            )
        )
        val request = result.request

        assertTrue(request.messages.last().content.contains("Continue your last message"))
        assertTrue(
            result.inspection.items.last().sources.any {
                it.kind == PromptSourceKind.ContinueNudge
            }
        )
        assertFalse(request.messages.any { it.content.contains("Write only Lyra") })
        assertEquals(LLMMessageRole.User, request.messages.last().role)
        assertEquals(
            "Lyra: Partial",
            request.messages[request.messages.lastIndex - 1].content
        )
    }

    @Test
    fun finalGroupPromptStaysWithinBudgetAndExplainsRemovedHistory() {
        val lyra = character(1, "Lyra")
        val mina = character(2, "Mina")
        val result = GroupChatPromptBuilder().buildWithMetadata(
            GroupChatPromptContext(
                session = GroupChatSession(
                    id = 1,
                    title = "Crew",
                    createTime = 1,
                    latestTime = 1,
                    userName = "Alex",
                    userDescription = ""
                ),
                members = listOf(member(lyra, 0), member(mina, 1)),
                speaker = mina,
                messages = listOf(
                    message(GroupChatMessage.Source.User, "Alex", "旧".repeat(300)),
                    message(GroupChatMessage.Source.Character, "Lyra", "早".repeat(300)),
                    message(GroupChatMessage.Source.User, "Alex", "Latest")
                ),
                provider = provider(contextTokens = 1_000, maxTokens = 100)
            )
        )

        assertTrue(result.inspection.finalTokenCount <= 900)
        assertTrue(result.request.messages.any { it.content.contains("Alex: Latest") })
        assertTrue(result.inspection.omittedItems.isNotEmpty())
    }

    @Test
    fun regenerateGenerationModeActivatesMatchingWorldInfoTrigger() {
        val lyra = character(1, "Lyra")
        val session = GroupChatSession(
            id = 1,
            title = "Crew",
            createTime = 1,
            latestTime = 1,
            userName = "Alex",
            userDescription = ""
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
            triggers = """["regenerate"]"""
        )
        val baseContext = GroupChatPromptContext(
            session = session,
            members = listOf(member(lyra, 0)),
            speaker = lyra,
            messages = listOf(
                message(GroupChatMessage.Source.User, "Alex", "Approach the station.")
            ),
            provider = provider(),
            candidateLorebookEntries = listOf(entry)
        )

        val normal = GroupChatPromptBuilder().build(baseContext)
        val regenerated = GroupChatPromptBuilder().build(
            baseContext.copy(generationMode = GroupChatGenerationMode.Regenerate)
        )

        assertFalse(normal.messages.any { it.content.contains(entry.content) })
        assertTrue(regenerated.messages.any { it.content.contains(entry.content) })
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
            postHistoryInstructions = ""
        )
    }

    private fun member(character: Character, order: Int): GroupChatMemberData {
        return GroupChatMemberData(
            relation = GroupChatMember(1, character.id, order),
            character = character
        )
    }

    private fun message(
        source: GroupChatMessage.Source,
        speaker: String,
        content: String
    ): GroupChatMessage {
        return GroupChatMessage(
            sessionId = 1,
            createTime = 1,
            source = source,
            content = content,
            speakerCharacterId = null,
            speakerNameSnapshot = speaker
        )
    }

    private fun provider(
        contextTokens: Int = 8192,
        maxTokens: Int = 512
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
            isEnabled = true
        )
    }
}
