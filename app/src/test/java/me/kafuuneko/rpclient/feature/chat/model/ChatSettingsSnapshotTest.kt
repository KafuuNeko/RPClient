package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.libs.room.model.SummaryVersion
import me.kafuuneko.rpclient.libs.room.model.SummaryInputSnapshot
import me.kafuuneko.rpclient.model.SummaryEditorState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证单聊脏检测与保存归一化、作者备注继承和摘要草稿的边界一致。 */
class ChatSettingsSnapshotTest {
    /** 未编辑或改回原值时无需提示离开确认。 */
    @Test
    fun unchangedAndRevertedDraftsAreClean() {
        val base = sampleSettings()
        assertFalse(base.hasUnsavedChanges())
        assertTrue(base.copy(titleDraft = "Another chat").hasUnsavedChanges())
        assertFalse(
            base.copy(titleDraft = "Another chat").copy(titleDraft = base.titleDraft)
                .hasUnsavedChanges()
        )
    }

    /** 标题空白回退原标题，备注与摘要忽略首尾空白，名称和描述仍精确保留空白。 */
    @Test
    fun whitespaceComparisonMatchesPersistenceRules() {
        val base = sampleSettings()
        assertFalse(base.copy(titleDraft = "  Chat  ").hasUnsavedChanges())
        assertFalse(base.copy(titleDraft = "   ").hasUnsavedChanges())
        assertFalse(base.copy(userNoteDraft = "  Keep tension  ").hasUnsavedChanges())
        assertFalse(
            base.copy(summary = base.summary.copy(text = "  Existing memory  ")).hasUnsavedChanges()
        )
        assertTrue(base.copy(userNoteDraft = "Changed note").hasUnsavedChanges())
        assertTrue(base.copy(userNameDraft = " ").hasUnsavedChanges())
        assertTrue(base.copy(userDescriptionDraft = " ").hasUnsavedChanges())
    }

    /** 人设与两个开关都属于显式保存表单。 */
    @Test
    fun personaAndSwitchChangesAreDirty() {
        val base = sampleSettings()
        assertTrue(base.copy(persona = PersonaSelection(selectedId = 2L)).hasUnsavedChanges())
        assertTrue(base.copy(includeCharacterExamples = false).hasUnsavedChanges())
        assertTrue(base.copy(autoSummaryPaused = true).hasUnsavedChanges())
    }

    /** 未编辑继承备注，或将无覆盖备注清空，都不能将继承值固化为会话覆盖。 */
    @Test
    fun inheritedCreatorNotesRemainInheritedWhenSavingOtherFields() {
        val base = sampleSettings()
        assertFalse(base.copy(titleDraft = "Changed title").hasCreatorNotesChange())
        assertFalse(base.copy(creatorNotesDraft = "").hasCreatorNotesChange())
        assertFalse(base.copy(creatorNotesDraft = "   ").hasUnsavedChanges())
        assertTrue(base.copy(creatorNotesDraft = "Session notes").hasCreatorNotesChange())
        assertTrue(base.copy(creatorNotesDraft = "Session notes").hasUnsavedChanges())
    }

    /** 清空已有覆盖会恢复角色默认备注；改回原覆盖则不再提交备注。 */
    @Test
    fun clearingCreatorNotesOverrideRestoresCharacterDefault() {
        val base =
            sampleSettings().copy(creatorNotesDraft = "Session notes").withCurrentAsSavedSnapshot()
        assertTrue(base.copy(creatorNotesDraft = "").hasCreatorNotesChange())
        assertTrue(base.copy(creatorNotesDraft = "   ").hasUnsavedChanges())
        assertFalse(
            base.copy(creatorNotesDraft = "Other").copy(creatorNotesDraft = "Session notes")
                .hasCreatorNotesChange()
        )
    }

    /** 手动摘要保持原保存基线，回到原记忆后摘要变为干净但其他修改仍需保存。 */
    @Test
    fun persistedSummaryUpdatesKeepOtherDraftsDirty() {
        val base = sampleSettings()
        val afterSummary = base.copy(
            summary = base.summary.generated(
                content = "Generated memory",
                coveredMessageId = 10L,
                inputSnapshot = SummaryInputSnapshot(emptyList(), "baseline"),
                sources = emptyList(),
            )
        )
        assertTrue(afterSummary.hasUnsavedChanges())
        assertTrue(afterSummary.copy(userNoteDraft = "Unsaved note").hasUnsavedChanges())
        assertFalse(
            afterSummary.copy(summary = requireNotNull(afterSummary.summary.restoreLocal()))
                .hasSummaryChange()
        )
        assertTrue(
            afterSummary.copy(summary = afterSummary.summary.copy(text = "")).hasUnsavedChanges()
        )
    }

    private fun sampleSettings(): ChatSettingsState = ChatSettingsState(
        titleDraft = "Chat",
        summary = SummaryEditorState(
            saved = SummaryVersion(
                content = "Existing memory",
                coveredMessageId = 5L
            )
        ),
        userNoteDraft = "Keep tension",
        persona = PersonaSelection(selectedId = 1L),
        creatorNotesDraft = "Character notes",
        inheritedCreatorNotes = "Character notes",
    ).withCurrentAsSavedSnapshot()
}
