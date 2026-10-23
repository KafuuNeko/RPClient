package me.kafuuneko.rpclient.libs.story

import com.google.gson.Gson
import me.kafuuneko.rpclient.libs.prompt.renderUserPersonaTemplate
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import me.kafuuneko.rpclient.libs.room.entity.StoryLorebookEntry
import me.kafuuneko.rpclient.libs.room.repository.StoryCharacterCandidate
import me.kafuuneko.rpclient.libs.room.repository.StoryLorebookEntryCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryCharacterActivatorTest {
    private val mActivator = StoryCharacterActivator()

    @Test
    fun alwaysAndAllMatchedAutoCharactersActivateInStableOrder() {
        val candidates = listOf(
            candidate(id = 2L, name = "Alice", order = 2, mode = StoryCharacter.ACTIVATION_AUTO),
            candidate(id = 1L, name = "Bob", order = 0, mode = StoryCharacter.ACTIVATION_ALWAYS),
            candidate(id = 3L, name = "林月", order = 1, mode = StoryCharacter.ACTIVATION_AUTO),
        )

        val result = mActivator.activate(candidates, "ALICE met 林月 beside the gate.")

        assertEquals(listOf(1L, 3L, 2L), result.map { it.candidate.character.id })
        assertEquals(StoryCharacterActivationReason.Always, result[0].reason)
        assertEquals(StoryCharacterActivationReason.Name, result[1].reason)
        assertEquals(StoryCharacterActivationReason.Name, result[2].reason)
    }

    @Test
    fun latinNameUsesWholeWordWhileCjkNameUsesSubstring() {
        val candidates = listOf(
            candidate(id = 1L, name = "Ann", order = 0, mode = StoryCharacter.ACTIVATION_AUTO),
            candidate(id = 2L, name = "林月", order = 1, mode = StoryCharacter.ACTIVATION_AUTO),
        )

        val result = mActivator.activate(candidates, "Annette 看见小林月亮起身。")

        assertEquals(listOf(2L), result.map { it.candidate.character.id })
    }

    @Test
    fun primaryCharacterAlwaysActivatesBeforeOtherCharacters() {
        val result = mActivator.activate(
            candidates = listOf(
                candidate(id = 1L, name = "Alice", order = 1, mode = StoryCharacter.ACTIVATION_AUTO),
                candidate(
                    id = 2L,
                    name = "Bob",
                    order = 0,
                    mode = StoryCharacter.ACTIVATION_PRIMARY,
                ),
            ),
            scanText = "No character name appears here.",
        )

        assertEquals(listOf(2L), result.map { it.candidate.character.id })
        assertEquals(StoryCharacterActivationReason.Primary, result.single().reason)
    }

    @Test
    fun promptFieldsResolveEachCharactersCardNameAndSharedUserIndependently() {
        val alice = mActivator
            .activate(
                candidates = listOf(
                    element = candidate(
                        id = 1L,
                        name = "Alice",
                        order = 0,
                        mode = StoryCharacter.ACTIVATION_ALWAYS,
                        description = "{{char}} protects {{ user }} and <USER>.",
                        personality = "<BOT> stays calm.",
                        scenario = "{{CHAR}} waits.",
                    ),
                ),
                scanText = "",
            )
            .single()
            .resolveForPrompt("Reader")
        val bob = mActivator
            .activate(
                candidates = listOf(candidate(id = 2L, name = "Bob", order = 0, mode = StoryCharacter.ACTIVATION_ALWAYS)),
                scanText = "",
            )
            .single()
            .resolveForPrompt("Reader")

        assertEquals("Alice", alice.name)
        assertEquals("Alice protects Reader and Reader.", alice.description)
        assertEquals("Alice stays calm.", alice.personality)
        assertEquals("Alice waits.", alice.scenario)
        assertEquals("Bob", bob.name)
    }

    @Test
    fun userPersonaResolvesUserButKeepsAmbiguousCharacterMacro() {
        assertEquals(
            "User Persona (Reader):\nReader observes {{char}}.",
            renderUserPersonaTemplate(
                template = "User Persona ({{user}}):\n{{persona}}",
                userName = "Reader",
                userDescription = "{{user}} observes {{char}}.",
                characterName = null,
            ),
        )
        assertEquals(
            "",
            renderUserPersonaTemplate(
                template = "{{persona}}",
                userName = "Reader",
                userDescription = "",
                characterName = null,
            ),
        )
    }

    @Test
    fun lorebookMacrosPreferFirstCharacterLinkedToLorebookThenPrimaryFallback() {
        val characterNames = listOf(
            candidate(id = 1L, name = "Alice", order = 0, mode = StoryCharacter.ACTIVATION_ALWAYS, lorebookId = 3L),
            candidate(id = 2L, name = "Bob", order = 1, mode = StoryCharacter.ACTIVATION_ALWAYS, lorebookId = 3L),
        ).lorebookCharacterNames()
        val linked = lorebookCandidate().resolveForPrompt(characterNames[3L], "Reader", Gson())
        val primary = lorebookCandidate().resolveForPrompt("Primary", "Reader", Gson())
        val unresolved = lorebookCandidate().resolveForPrompt(characterName = null, userName = "Reader", gson = Gson())

        assertEquals("Alice met Reader.", linked.content)
        assertEquals(listOf("Alice", "Reader"), linked.getKeywordList())
        assertEquals("Primary met Reader.", primary.content)
        assertEquals("{{char}} met Reader.", unresolved.content)
        assertEquals(listOf("{{char}}", "Reader"), unresolved.getKeywordList())
    }

    private fun candidate(
        id: Long,
        name: String,
        order: Int,
        mode: Int,
        description: String = "Description",
        personality: String = "Personality",
        scenario: String = "Scenario",
        lorebookId: Long = 0L,
    ): StoryCharacterCandidate {
        return StoryCharacterCandidate(
            relation = StoryCharacter(storyId = 1L, characterId = id, sortOrder = order, activationMode = mode),
            character = Character(
                id = id,
                name = name,
                avatar = "",
                characterTags = "[]",
                description = description,
                personality = personality,
                scenario = scenario,
                firstMessages = "",
                examplesOfDialogue = "",
                postHistoryInstructions = "",
                characterLorebookId = lorebookId,
            ),
        )
    }

    private fun lorebookCandidate(): StoryLorebookEntryCandidate {
        return StoryLorebookEntryCandidate(
            relation = StoryLorebookEntry(
                storyId = 1L,
                lorebookEntryId = 2L,
            ),
            entry = LorebookEntry(
                id = 2L,
                lorebookId = 3L,
                name = "Entry",
                keywords = "[\"{{char}}\",\"{{user}}\"]",
                secondaryKeywords = "[\"<BOT>\"]",
                order = 0,
                depth = 0,
                category = "[]",
                content = "{{char}} met {{user}}.",
            ),
        )
    }
}
