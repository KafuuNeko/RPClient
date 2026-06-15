package me.kafuuneko.rpclient.feature.chatcreate.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatCreateFormTest {
    @Test
    fun selectingCharacterReplacesLinkedLorebookEntriesAndSelectsFirstMessage() {
        val form = ChatCreateForm(
            selectedCharacterId = 1L,
            selectedFirstMessageIndex = 2,
            selectedLorebookEntryIds = setOf(10L, 11L, 99L)
        )

        val selected = form.selectCharacter(
            characterId = 2L,
            hasFirstMessage = true,
            previousLinkedLorebookEntryIds = setOf(10L, 11L),
            linkedLorebookEntryIds = setOf(20L, 21L)
        )

        assertEquals(2L, selected.selectedCharacterId)
        assertEquals(0, selected.selectedFirstMessageIndex)
        assertEquals(setOf(20L, 21L, 99L), selected.selectedLorebookEntryIds)
    }

    @Test
    fun selectingCharacterWithoutFirstMessageClearsFirstMessageSelection() {
        val selected = ChatCreateForm(selectedFirstMessageIndex = 1).selectCharacter(
            characterId = 2L,
            hasFirstMessage = false
        )

        assertEquals(null, selected.selectedFirstMessageIndex)
    }
}
