package me.kafuuneko.rpclient.libs.regex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexStorageMigrationTest {
    @Test
    fun authorizedPresetScriptsAppendAfterGlobalAndKeepEnabledState() {
        val result = mergeLegacyGlobalAndPreset(
            globalScripts = listOf(script("global")),
            presetScripts = listOf(script("preset"), script(id = "disabled", disabled = true)),
            presetAuthorized = true,
        )

        assertEquals(listOf("global", "preset", "disabled"), result.map { it.id })
        assertFalse(result[1].disabled)
        assertTrue(result[2].disabled)
    }

    @Test
    fun unauthorizedPresetScriptsAreMigratedDisabled() {
        val result = mergeLegacyGlobalAndPreset(
            globalScripts = emptyList(),
            presetScripts = listOf(script("preset")),
            presetAuthorized = false,
        )

        assertEquals("preset", result.single().id)
        assertTrue(result.single().disabled)
    }

    @Test
    fun blankAndConflictingIdsAreReplacedAcrossMergedScope() {
        val generatedIds = ArrayDeque(listOf("global", "migrated-one", "migrated-two"))
        val result = mergeLegacyGlobalAndPreset(
            globalScripts = listOf(script("global")),
            presetScripts = listOf(script("global"), script("")),
            presetAuthorized = true,
            createId = { generatedIds.removeFirst() },
        )

        assertEquals(
            listOf("global", "migrated-one", "migrated-two"),
            result.map { it.id },
        )
    }

    private fun script(id: String, disabled: Boolean = false) = RegexScript(
        id = id,
        scriptName = id,
        findRegex = "/x/g",
        replaceString = "y",
        placement = listOf(RegexPlacement.UserInput.value),
        disabled = disabled,
    )
}
