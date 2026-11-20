package me.kafuuneko.rpclient.feature.storycreate.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** 保护创建故事时影响 Prompt 的默认主角与既有角色模式。 */
class StoryCreateFormTest {
    /** 首个勾选的角色成为主角，即使其 ID 比后续勾选的角色大。 */
    @Test
    fun firstCheckedCharacterBecomesPrimary() {
        val form = StoryCreateForm().applyCharacterSelection(selectedIdsInOrder = listOf(9L, 2L))

        assertEquals(StoryCreateCharacterActivationMode.Primary, form.activationModeOf(9L))
        assertEquals(StoryCreateCharacterActivationMode.Auto, form.activationModeOf(2L))
    }

    /** 保留角色的显式模式优先于新增角色的默认主角规则。 */
    @Test
    fun retainedActivationModesArePreserved() {
        val form = StoryCreateForm(
            characterActivationModes = mapOf(
                1L to StoryCreateCharacterActivationMode.Primary,
                2L to StoryCreateCharacterActivationMode.Always,
            ),
        ).applyCharacterSelection(selectedIdsInOrder = listOf(3L, 2L, 1L))

        assertEquals(StoryCreateCharacterActivationMode.Primary, form.activationModeOf(1L))
        assertEquals(StoryCreateCharacterActivationMode.Always, form.activationModeOf(2L))
        assertEquals(StoryCreateCharacterActivationMode.Auto, form.activationModeOf(3L))
    }
}
