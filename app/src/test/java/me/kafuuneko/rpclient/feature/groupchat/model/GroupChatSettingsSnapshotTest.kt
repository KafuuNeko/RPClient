package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证群聊设置未保存变更检测与保存落库规则保持一致。 */
class GroupChatSettingsSnapshotTest {
    /** 未修改表单或改回原值时不应触发离开保存提示。 */
    @Test
    fun unmodifiedOrRevertedDraftHasNoUnsavedChanges() {
        val base = sampleSettingsState()

        assertFalse(base.hasUnsavedChanges())
        assertTrue(base.copy(titleDraft = "Updated Crew").hasUnsavedChanges())
        assertFalse(
            base.copy(titleDraft = "Updated Crew")
                .copy(titleDraft = "Starlight Crew")
                .hasUnsavedChanges(),
        )
    }

    /** 保存时会裁剪或回退原值的字段应忽略无效空白，非裁剪字段保留精确比对。 */
    @Test
    fun textNormalizationMatchesSaveRules() {
        val base = sampleSettingsState()

        // 标题与裁剪字段仅增减首尾空白，以及标题清空回退原标题，保存后均不改变落库值。
        assertFalse(base.copy(titleDraft = "  Starlight Crew  ").hasUnsavedChanges())
        assertFalse(base.copy(titleDraft = "   ").hasUnsavedChanges())
        assertFalse(base.copy(scenarioDraft = "  Orbital station  ").hasUnsavedChanges())
        assertFalse(base.copy(userNoteDraft = "  Keep tension  ").hasUnsavedChanges())
        assertFalse(base.copy(systemPromptDraft = "  System  ").hasUnsavedChanges())
        assertFalse(base.copy(groupNudgePromptDraft = "  Nudge  ").hasUnsavedChanges())
        assertFalse(base.copy(newGroupChatPromptDraft = "  New  ").hasUnsavedChanges())

        // 名称覆盖与人设描述按原文保存，空白差异也会实际落库。
        assertTrue(base.copy(userNameDraft = " ").hasUnsavedChanges())
        assertTrue(base.copy(userDescriptionDraft = "Captain ").hasUnsavedChanges())
    }

    /** 切换策略、卡片模式、人设或开关均应识别为未保存修改。 */
    @Test
    fun selectionAndSwitchChangesAreDetected() {
        val base = sampleSettingsState()

        // 验证枚举选项与人设切换均能触发未保存状态。
        assertTrue(
            base.copy(persona = PersonaSelection(selectedId = 2L)).hasUnsavedChanges(),
        )
        assertTrue(
            base.copy(activationStrategy = GroupChatActivationStrategy.List).hasUnsavedChanges(),
        )
        assertTrue(
            base.copy(characterCardMode = GroupChatCharacterCardMode.Join).hasUnsavedChanges(),
        )
        // 验证全部群聊布尔开关变更均能触发未保存状态。
        assertTrue(base.copy(allowSelfResponses = true).hasUnsavedChanges())
        assertTrue(base.copy(includeMutedCards = true).hasUnsavedChanges())
        assertTrue(base.copy(includeCharacterExamples = false).hasUnsavedChanges())
        assertTrue(base.copy(autoModeEnabled = true).hasUnsavedChanges())
        assertTrue(base.copy(trimOtherSpeakers = false).hasUnsavedChanges())
        assertTrue(base.copy(autoSummaryPaused = true).hasUnsavedChanges())
    }

    /** 手动摘要已同步落库时不误报未保存，仅手工改动摘要正文时提示保存。 */
    @Test
    fun summaryComparisonTracksSavedSummary() {
        val base = sampleSettingsState()

        // 立即摘要会同步刷新草稿与已保存摘要。
        val afterManualSummary = base.copy(
            summaryDraft = "Fresh generated summary",
            savedSummary = "Fresh generated summary",
        )
        assertFalse(afterManualSummary.hasUnsavedChanges())

        // 仅首尾空白差异不视为实质修改，正文改变时触发提示。
        assertFalse(base.copy(summaryDraft = "  Existing summary  ").hasUnsavedChanges())
        assertTrue(base.copy(summaryDraft = "Edited summary").hasUnsavedChanges())
    }

    /** 仅成员或世界书等即时持久化列表变化，不应触发设置表单保存提示。 */
    @Test
    fun immediatePersistedListsDoNotMarkSettingsDirty() {
        val base = sampleSettingsState()
        val updatedMembers = base.copy(
            availableCharacters = listOf(
                GroupChatAvailableCharacterItem(
                    id = 7L,
                    name = "Lyra",
                    alreadyMember = true,
                ),
            ),
        )

        assertFalse(updatedMembers.hasUnsavedChanges())
    }

    private fun sampleSettingsState(): GroupChatSettingsState {
        // 构造已同步基线快照的群聊设置基准态。
        return GroupChatSettingsState(
            persona = PersonaSelection(selectedId = 1L),
            activationStrategy = GroupChatActivationStrategy.Natural,
            characterCardMode = GroupChatCharacterCardMode.Swap,
            allowSelfResponses = false,
            includeMutedCards = false,
            includeCharacterExamples = true,
            autoModeEnabled = false,
            trimOtherSpeakers = true,
            scenarioDraft = "Orbital station",
            userNoteDraft = "Keep tension",
            userNameDraft = "",
            userDescriptionDraft = "Captain",
            summaryDraft = "Existing summary",
            savedSummary = "Existing summary",
            autoSummaryPaused = false,
            systemPromptDraft = "System",
            groupNudgePromptDraft = "Nudge",
            newGroupChatPromptDraft = "New",
            titleDraft = "Starlight Crew",
        ).withCurrentAsSavedSnapshot()
    }
}
