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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatPromptBuilderTest {
    @Test
    fun promptIdentifiesCurrentSpeakerAndEveryHistoricalSpeaker() {
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
        assertTrue(content.contains("Current responding character: Mina"))
        assertTrue(content.contains("Group members: Lyra, Mina"))
        assertTrue(content.contains("Alex: Look outside."))
        assertTrue(content.contains("Lyra: I see a station."))
        assertTrue(content.contains("Write only Mina's next reply"))
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
