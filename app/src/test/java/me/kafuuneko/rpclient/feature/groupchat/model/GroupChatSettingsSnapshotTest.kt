package me.kafuuneko.rpclient.feature.groupchat.model

import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot
import me.kafuuneko.rpclient.model.SummaryEditorState
import me.kafuuneko.rpclient.model.toUiState
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

    /** 手动摘要需显式保存，忽略正文首尾空白但不能忽略覆盖位置变化。 */
    @Test
    fun summaryComparisonTracksSavedSummary() {
        val editor = sampleSummaryEditor()
        val base = sampleSettingsState()

        // 即使正文相同，覆盖位置推进也会改变后续上下文。
        val afterManualSummary = base.copy(
            summary = editor.generated(
                content = "Existing summary",
                coveredMessageId = 10L,
                inputSnapshot = SummaryInputSnapshot(emptyList(), "baseline"),
                sources = emptyList(),
            ).toUiState()
        )
        assertTrue(afterManualSummary.hasUnsavedChanges())

        // 仅首尾空白差异不视为实质修改，正文改变时触发提示。
        assertFalse(
            base.copy(summary = editor.copy(text = "  Existing summary  ").toUiState())
                .hasUnsavedChanges()
        )
        assertTrue(
            base.copy(summary = editor.copy(text = "Edited summary").toUiState())
                .hasUnsavedChanges()
        )
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
        val lorebook = GroupChatLorebookGroupItem(
            lorebookId = 8L,
            lorebookName = "Station",
            entries = listOf(
                GroupChatLorebookEntryItem(
                    id = 9L,
                    lorebookId = 8L,
                    lorebookName = "Station",
                    name = "Dock",
                    content = "Station dock",
                    keywords = emptyList(),
                    secondaryKeywords = emptyList(),
                    constant = true,
                    order = 0,
                    depth = 0,
                    enabled = true,
                ),
            ),
        )
        // 世界书候选更新与启用集合变化均已即时写库，不属于待保存表单。
        assertFalse(base.copy(lorebookGroups = listOf(lorebook)).hasUnsavedChanges())
        assertFalse(base.copy(enabledLorebookGroups = listOf(lorebook)).hasUnsavedChanges())
        assertFalse(
            updatedMembers.copy(
                lorebookGroups = listOf(lorebook),
                enabledLorebookGroups = listOf(lorebook),
            ).hasUnsavedChanges(),
        )
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
            summary = sampleSummaryEditor().toUiState(),
            autoSummaryPaused = false,
            systemPromptDraft = "System",
            groupNudgePromptDraft = "Nudge",
            newGroupChatPromptDraft = "New",
            titleDraft = "Starlight Crew",
        ).withCurrentAsSavedSnapshot()
    }

    /** 与持久化记忆同步的摘要编辑基线。 */
    private fun sampleSummaryEditor(): SummaryEditorState = SummaryEditorState(
        saved = SummaryVersion(content = "Existing summary", coveredMessageId = 5L),
    )
}
