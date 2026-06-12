package me.kafuuneko.rpclient.feature.worldbookentryedit.model

import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorldBookEntryEditFormTest {
    @Test
    fun formRoundTripPreservesCompatibilityFieldsAndNullableFlags() {
        val entry = completeEntry()

        val saved = WorldBookEntryEditForm.from(entry).toLorebookEntryOrNull()

        assertNotNull(saved)
        assertEquals(entry, saved)
    }

    @Test
    fun touchingNullableFlagsStoresExplicitValues() {
        val saved = WorldBookEntryEditForm.from(completeEntry())
            .copy(matchWholeWords = false, caseSensitive = false)
            .toLorebookEntryOrNull()

        assertEquals(false, saved?.matchWholeWords)
        assertEquals(false, saved?.caseSensitive)
    }

    private fun completeEntry(): LorebookEntry {
        return LorebookEntry(
            id = 21L,
            lorebookId = 12L,
            name = "Entry",
            keywords = """["alpha","beta"]""",
            secondaryKeywords = """["gamma"]""",
            constant = true,
            order = 77,
            depth = 3,
            category = """["setting"]""",
            content = "Entry content",
            disabled = true,
            position = LorebookEntry.POSITION_OUTLET,
            role = LorebookEntry.ROLE_ASSISTANT,
            probability = 63,
            ignoreBudget = true,
            scanDepth = 8,
            selectiveLogic = LorebookEntry.LOGIC_AND_ALL,
            matchWholeWords = null,
            caseSensitive = null,
            useGroupScoring = true,
            group = "weather,location",
            groupOverride = true,
            groupWeight = 45,
            preventRecursion = true,
            delayUntilRecursion = true,
            sticky = 4,
            cooldown = 5,
            delay = 6,
            outletName = "details",
            triggers = """["normal","continue"]""",
            matchPersonaDescription = true,
            matchCharacterDescription = true,
            matchCharacterPersonality = true,
            matchCharacterDepthPrompt = true,
            matchScenario = true,
            matchCreatorNotes = true,
            extensionsJson = """{"custom_extension":{"value":7}}""",
            rawJson = """{"custom_top_level":{"preserve":true}}"""
        )
    }
}
