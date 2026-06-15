package me.kafuuneko.rpclient.libs.groupchat

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupChatGreetingPlannerTest {
    private val planner = GroupChatGreetingPlanner()
    private val candidates = listOf(
        GroupChatGreetingCandidate(
            characterId = 1,
            characterName = "Lyra",
            greetings = listOf("Hello from {{char}}.", "Welcome, {{user}}.")
        ),
        GroupChatGreetingCandidate(
            characterId = 2,
            characterName = "Mina",
            greetings = listOf("{{char}} opens the archive.")
        )
    )

    @Test
    fun randomModeCreatesOneResolvedGreetingPerCharacterInMemberOrder() {
        val messages = planner.plan(
            candidates = candidates,
            selection = GroupChatGreetingSelection.RandomPerCharacter,
            userName = "Alex",
            random = Random(1)
        )

        assertEquals(listOf(1L, 2L), messages.map { it.characterId })
        assertTrue(messages[0].content in listOf("Hello from Lyra.", "Welcome, Alex."))
        assertEquals("Mina opens the archive.", messages[1].content)
    }

    @Test
    fun manualModeCreatesOnlyTheChosenGreeting() {
        val messages = planner.plan(
            candidates = candidates,
            selection = GroupChatGreetingSelection.Manual(
                characterId = 1,
                greetingIndex = 1
            ),
            userName = "Alex"
        )

        assertEquals(1, messages.size)
        assertEquals("Lyra", messages.single().characterName)
        assertEquals("Welcome, Alex.", messages.single().content)
    }

    @Test
    fun customModeAttributesAndResolvesTheMessageForTheChosenCharacter() {
        val messages = planner.plan(
            candidates = candidates,
            selection = GroupChatGreetingSelection.Custom(
                characterId = 2,
                content = "  {{char}} asks {{user}} to sit down.  "
            ),
            userName = "Alex"
        )

        assertEquals(2L, messages.single().characterId)
        assertEquals("Mina asks Alex to sit down.", messages.single().content)
    }

    @Test
    fun noneModeCreatesNoMessages() {
        assertEquals(
            emptyList<GroupChatOpeningMessage>(),
            planner.plan(
                candidates = candidates,
                selection = GroupChatGreetingSelection.None,
                userName = "Alex"
            )
        )
    }
}
