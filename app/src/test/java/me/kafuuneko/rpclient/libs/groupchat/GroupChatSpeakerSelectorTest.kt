package me.kafuuneko.rpclient.libs.groupchat

import kotlin.random.Random
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupChatSpeakerSelectorTest {
    private val selector = GroupChatSpeakerSelector()

    @Test
    fun listStrategyReturnsEveryActiveMemberInOrder() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1),
            member(3, "Rowan", order = 2, muted = true)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.List),
            members = members,
            messages = emptyList(),
            userInput = "",
            manualCharacterId = null,
            random = Random(1)
        )

        assertEquals(listOf(1L, 2L), selected.map { it.character.id })
    }

    @Test
    fun pooledStrategyPrioritizesMemberWhoHasNotSpokenSinceUser() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )
        val messages = listOf(
            message(GroupChatMessage.Source.User, null, "You"),
            message(GroupChatMessage.Source.Character, 1, "Lyra")
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = messages,
            userInput = "",
            manualCharacterId = null,
            random = Random(2)
        )

        assertEquals(2L, selected.single().character.id)
    }

    @Test
    fun naturalStrategyAlwaysActivatesMentionedCharacter() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            userInput = "Mina, check the archive.",
            manualCharacterId = null,
            random = Random(3)
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun naturalStrategyMatchesNameTokensWithoutSubstringFalsePositives() {
        val members = listOf(
            member(1, "Ann", order = 0),
            member(2, "Misaka Mikoto", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            userInput = "Misaka should inspect the announcement.",
            manualCharacterId = null,
            random = Random(8)
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun manualStrategyRequiresAnExplicitActiveSpeaker() {
        val members = listOf(
            member(1, "Lyra", order = 0),
            member(2, "Mina", order = 1)
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            userInput = "",
            manualCharacterId = null,
            random = Random(4)
        )

        assertEquals(emptyList<GroupChatMemberData>(), selected)
    }

    private fun session(
        strategy: GroupChatSession.ActivationStrategy
    ): GroupChatSession {
        return GroupChatSession(
            id = 1,
            title = "Group",
            createTime = 1,
            latestTime = 1,
            userName = "You",
            userDescription = "",
            activationStrategy = strategy
        )
    }

    private fun member(
        id: Long,
        name: String,
        order: Int,
        muted: Boolean = false
    ): GroupChatMemberData {
        return GroupChatMemberData(
            relation = GroupChatMember(
                sessionId = 1,
                characterId = id,
                sortOrder = order,
                muted = muted
            ),
            character = character(id, name)
        )
    }

    private fun character(id: Long, name: String): Character {
        return Character(
            id = id,
            name = name,
            avatar = "",
            characterTags = "[]",
            description = "",
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = ""
        )
    }

    private fun message(
        source: GroupChatMessage.Source,
        speakerId: Long?,
        speakerName: String
    ): GroupChatMessage {
        return GroupChatMessage(
            id = (speakerId ?: 10L),
            sessionId = 1,
            createTime = 1,
            source = source,
            content = "Message",
            speakerCharacterId = speakerId,
            speakerNameSnapshot = speakerName
        )
    }
}
