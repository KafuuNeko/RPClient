package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMember
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.GroupChatMemberData
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class GroupChatSpeakerSelectorTest {
    private val selector = GroupChatSpeakerSelector()

    @Test
    fun listStrategyReturnsEveryActiveMemberInOrder() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
            member(id = 3, name = "Rowan", order = 2, muted = true),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.List),
            members = members,
            messages = emptyList(),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(1),
        )

        assertEquals(listOf(1L, 2L), selected.map { it.character.id })
    }

    @Test
    fun pooledStrategyPrioritizesMemberWhoHasNotSpokenSinceUser() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )
        val messages = listOf(
            message(source = GroupChatMessage.Source.User, speakerId = null, speakerName = "You"),
            message(source = GroupChatMessage.Source.Character, speakerId = 1, speakerName = "Lyra"),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = messages,
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(2),
        )

        assertEquals(2L, selected.single().character.id)
    }

    @Test
    fun naturalStrategyAlwaysActivatesMentionedCharacter() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "Mina, check the archive.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(3),
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun naturalStrategyMatchesNameTokensWithoutSubstringFalsePositives() {
        val members = listOf(
            member(id = 1, name = "Ann", order = 0),
            member(id = 2, name = "Misaka Mikoto", order = 1),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "Misaka should inspect the announcement.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(8),
        )

        assertEquals(true, selected.any { it.character.id == 2L })
    }

    @Test
    fun naturalStrategyRecognizesCjkNameInsideSentence() {
        val members = listOf(
            member(id = 1, name = "小洛", order = 0, talkativeness = 0.0),
            member(id = 2, name = "大列巴", order = 1, talkativeness = 1.0),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = emptyList(),
            activationText = "让小洛回答这个问题。",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(9),
        )

        assertEquals(true, selected.any { it.character.id == 1L })
    }

    @Test
    fun manualStrategyWithUserInputRequiresAnExplicitActiveSpeaker() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            activationText = "Question",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(4),
        )

        assertEquals(emptyList<GroupChatMemberData>(), selected)
    }

    @Test
    fun manualStrategyWithEmptyInputSelectsOneRandomSpeaker() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Manual),
            members = members,
            messages = emptyList(),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(4),
        )

        assertEquals(1, selected.size)
        assertEquals(true, selected.single() in members)
    }

    @Test
    fun naturalStrategyAllowsUserToMentionPreviousSpeaker() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )

        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Natural),
            members = members,
            messages = listOf(
                element = message(source = GroupChatMessage.Source.Character, speakerId = 1, speakerName = "Lyra"),
            ),
            activationText = "Lyra, answer this.",
            isUserInput = true,
            manualCharacterId = null,
            random = Random(5),
        )

        assertEquals(true, selected.any { it.character.id == 1L })
    }

    @Test
    fun pooledStrategyAvoidsImmediateRepeatAfterEveryoneSpoke() {
        val members = listOf(
            member(id = 1, name = "Lyra", order = 0),
            member(id = 2, name = "Mina", order = 1),
        )
        val selected = selector.select(
            session = session(GroupChatSession.ActivationStrategy.Pooled),
            members = members,
            messages = listOf(
                message(source = GroupChatMessage.Source.User, speakerId = null, speakerName = "You"),
                message(source = GroupChatMessage.Source.Character, speakerId = 1, speakerName = "Lyra"),
                message(source = GroupChatMessage.Source.Character, speakerId = 2, speakerName = "Mina"),
            ),
            activationText = "",
            isUserInput = false,
            manualCharacterId = null,
            random = Random(6),
        )

        assertEquals(1L, selected.single().character.id)
    }

    private fun session(
        strategy: GroupChatSession.ActivationStrategy,
    ): GroupChatSession {
        return GroupChatSession(
            id = 1,
            title = "Group",
            createTime = 1,
            latestTime = 1,
            userName = "You",
            userDescription = "",
            activationStrategy = strategy,
        )
    }

    private fun member(
        id: Long,
        name: String,
        order: Int,
        muted: Boolean = false,
        talkativeness: Double? = null,
    ): GroupChatMemberData {
        return GroupChatMemberData(
            relation = GroupChatMember(
                sessionId = 1,
                characterId = id,
                sortOrder = order,
                muted = muted,
            ),
            character = character(id, name, talkativeness),
        )
    }

    private fun character(
        id: Long,
        name: String,
        talkativeness: Double? = null,
    ): Character {
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
            postHistoryInstructions = "",
            extensionsJson = talkativeness
                ?.let { """{"talkativeness":$it}""" }
                ?: "{}",
        )
    }

    private fun message(
        source: GroupChatMessage.Source,
        speakerId: Long?,
        speakerName: String,
    ): GroupChatMessage {
        return GroupChatMessage(
            id = (speakerId ?: 10L),
            sessionId = 1,
            createTime = 1,
            source = source,
            content = "Message",
            speakerCharacterId = speakerId,
            speakerNameSnapshot = speakerName,
        )
    }
}
