package me.kafuuneko.rpclient.libs.upgrade

import me.kafuuneko.rpclient.libs.prompt.PromptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 保护跨版本迁移的缺键、空白与原文兼容规则。 */
class LegacyPromptSnapshotTest {
    @Test
    fun explicitEmptyWhitespaceAndMacrosArePreservedWithoutMaterializingDefaults() {
        val snapshot = LegacyPromptSnapshot.read(
            mapOf(
                "mainPrompt" to "",
                "auxiliaryPrompt" to " \n\t ",
                "storyMainPrompt" to "\n故事 {{char}} — {{user}}\r\n",
                "worldInfoFormat" to "{0}",
                "unrelatedPreference" to 4,
            )
        )
        assertTrue(snapshot.hasSavedValues)
        assertEquals(3, snapshot.values.size)
        assertEquals("", snapshot.values.getValue("main"))
        assertEquals(" \n\t ", snapshot.values.getValue("auxiliary"))
        assertEquals("\n故事 {{char}} — {{user}}\r\n", snapshot.values.getValue("story_main"))
        assertFalse(snapshot.values.containsKey("world_info_format"))
        assertFalse(snapshot.values.containsKey("story_continuation_guidance"))
    }

    @Test
    fun absenceAndExplicitDefaultRemainDistinct() {
        assertFalse(LegacyPromptSnapshot.read(mapOf("themeMode" to "dark")).hasSavedValues)
        assertTrue(LegacyPromptSnapshot.read(mapOf("mainPrompt" to PromptType.Main.defaultValue)).hasSavedValues)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSavedTypeIsRejectedInsteadOfLosingContent() {
        LegacyPromptSnapshot.read(mapOf("mainPrompt" to 12))
    }
}
