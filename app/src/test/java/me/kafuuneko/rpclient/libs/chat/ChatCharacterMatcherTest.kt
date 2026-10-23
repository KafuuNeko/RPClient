package me.kafuuneko.rpclient.libs.chat

import me.kafuuneko.rpclient.libs.room.entity.Character
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatCharacterMatcherTest {
    @Test
    fun uniqueFingerprintTakesPriorityOverName() {
        val expected = character(id = 1L, name = "Renamed", description = "Original card")
        val sameName = character(id = 2L, name = "Seraphina", description = "Different card")
        val archive = archive(
            name = "Seraphina",
            fingerprint = ChatCharacterMatcher.fingerprintOf(expected),
        )

        assertEquals(
            expected.id,
            ChatCharacterMatcher.suggestCharacterId(archive, listOf(expected, sameName)),
        )
    }

    @Test
    fun uniqueNameIsSuggestedButDuplicateNameIsNot() {
        val first = character(id = 1L, name = "Seraphina", description = "First")
        val second = character(id = 2L, name = "Other", description = "Second")
        val archive = archive(name = "seraphina", fingerprint = null)

        assertEquals(
            first.id,
            ChatCharacterMatcher.suggestCharacterId(archive, listOf(first, second)),
        )
        assertNull(
            ChatCharacterMatcher.suggestCharacterId(
                archive = archive,
                characters = listOf(first, second.copy(id = 3L, name = "SERAPHINA")),
            ),
        )
    }

    private fun archive(name: String, fingerprint: String?): ChatArchive {
        return ChatArchive(
            title = "Chat",
            createTime = 1L,
            latestTime = 1L,
            userName = "You",
            userDescription = "",
            userNote = "",
            creatorNotes = null,
            lorebookEntrySet = "[]",
            worldInfoStateJson = "{}",
            autoSummaryPaused = false,
            characterNameHint = name,
            characterFingerprint = fingerprint,
            messages = emptyList(),
            summary = null,
        )
    }

    private fun character(id: Long, name: String, description: String): Character {
        return Character(
            id = id,
            name = name,
            avatar = "",
            characterTags = "[]",
            description = description,
            personality = "",
            scenario = "",
            firstMessages = "",
            examplesOfDialogue = "",
            postHistoryInstructions = "",
        )
    }
}
