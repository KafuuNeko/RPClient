package me.kafuuneko.rpclient.feature.storyeditor.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDestination
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat

/** 分卷/章节编辑器可接收的用户行为和生命周期意图。 */
sealed class StoryEditorUiIntent {
    /** 从摘要额度提示前往全局设置。 */
    data object OpenSummarySettings : StoryEditorUiIntent()

    data class Init(val storyId: Long, val searchChapterId: Long? = null, val searchQuery: String = "") : StoryEditorUiIntent()

    data object Back : StoryEditorUiIntent()

    data class EditorSnapshotChanged(val snapshot: StoryEditorSnapshot) : StoryEditorUiIntent()

    data object FlushDraft : StoryEditorUiIntent()

    data object RetrySave : StoryEditorUiIntent()

    data object CopyConflictDraft : StoryEditorUiIntent()

    data object ReloadAfterConflict : StoryEditorUiIntent()

    data class OpenStoryOutline(val snapshot: StoryEditorSnapshot) : StoryEditorUiIntent()

    data object CloseStoryOutline : StoryEditorUiIntent()

    data class SelectStoryChapter(val chapterId: Long) : StoryEditorUiIntent()

    data object ShowCreateVolumeDialog : StoryEditorUiIntent()

    data class ShowCreateChapterDialog(val volumeId: Long?) : StoryEditorUiIntent()

    data class ShowRenameVolumeDialog(val volumeId: Long) : StoryEditorUiIntent()

    data class ShowRenameChapterDialog(val chapterId: Long) : StoryEditorUiIntent()

    data class ChangeStructureTitle(val value: String) : StoryEditorUiIntent()

    data object ConfirmStructureTitle : StoryEditorUiIntent()

    data class ShowDeleteVolumeDialog(val volumeId: Long) : StoryEditorUiIntent()

    data class ShowDeleteChapterDialog(val chapterId: Long) : StoryEditorUiIntent()

    data object ConfirmDeleteVolume : StoryEditorUiIntent()

    data object ConfirmDeleteChapter : StoryEditorUiIntent()

    data class MoveStoryVolume(val volumeId: Long, val offset: Int) : StoryEditorUiIntent()

    data class MoveStoryChapter(val chapterId: Long, val offset: Int) : StoryEditorUiIntent()

    data class DragStoryChapter(
        val chapterId: Long,
        val target: StoryChapterDropTarget
    ) : StoryEditorUiIntent()

    data object CommitStoryChapterOrder : StoryEditorUiIntent()

    data class ShowMoveStoryChapterDialog(val chapterId: Long) : StoryEditorUiIntent()

    data class SelectChapterDestination(
        val destination: StoryChapterDestination
    ) : StoryEditorUiIntent()

    data object ConfirmMoveStoryChapter : StoryEditorUiIntent()

    data object OpenStorySettings : StoryEditorUiIntent()

    data object CloseStorySettings : StoryEditorUiIntent()

    data class SelectSettingsSection(val section: StorySettingsSection) : StoryEditorUiIntent()

    data class ChangeMemory(val value: String) : StoryEditorUiIntent()

    data class ChangeSummary(val value: String) : StoryEditorUiIntent()

    data class ChangeAuthorNote(val value: String) : StoryEditorUiIntent()

    data class SetIncludeUserPersona(val enabled: Boolean) : StoryEditorUiIntent()

    data object SummarizeStory : StoryEditorUiIntent()

    data object CancelStorySummary : StoryEditorUiIntent()

    data object ConfirmStorySummary : StoryEditorUiIntent()

    data class ToggleStoryCharacter(val characterId: Long) : StoryEditorUiIntent()

    data class SetCharacterActivationMode(
        val characterId: Long,
        val activationMode: StoryCharacterActivationMode
    ) : StoryEditorUiIntent()

    data class MoveStoryCharacter(val characterId: Long, val offset: Int) : StoryEditorUiIntent()

    data class ToggleLorebook(val lorebookId: Long) : StoryEditorUiIntent()

    data class ToggleLorebookEntry(val entryId: Long) : StoryEditorUiIntent()

    data object SaveStorySettings : StoryEditorUiIntent()

    data class ChangeContinuationGuidance(val value: String) : StoryEditorUiIntent()

    data class ContinueStory(val snapshot: StoryEditorSnapshot) : StoryEditorUiIntent()

    data object StopGeneration : StoryEditorUiIntent()

    data object ToggleGenerationReasoning : StoryEditorUiIntent()

    data object InsertRecoverablePartial : StoryEditorUiIntent()

    data object CopyRecoverablePartial : StoryEditorUiIntent()

    data object DiscardRecoverablePartial : StoryEditorUiIntent()

    data object UndoLastEdit : StoryEditorUiIntent()

    data object RedoLastEdit : StoryEditorUiIntent()

    data object OpenPromptInspector : StoryEditorUiIntent()

    data class CopyPromptItem(val text: String) : StoryEditorUiIntent()

    data object OpenFileActions : StoryEditorUiIntent()

    data object ImportTextClick : StoryEditorUiIntent()

    data class ImportTextResult(val uri: Uri) : StoryEditorUiIntent()

    data object ImportStoryClick : StoryEditorUiIntent()

    data class ImportStoryResult(val uri: Uri) : StoryEditorUiIntent()

    data class ChangeImportTitle(val value: String) : StoryEditorUiIntent()

    data object ConfirmImport : StoryEditorUiIntent()

    data class ExportTextClick(val format: StoryTextExportFormat) : StoryEditorUiIntent()

    data class ExportTextResult(val uri: Uri) : StoryEditorUiIntent()

    data object ExportStoryClick : StoryEditorUiIntent()

    data class ExportStoryResult(val uri: Uri) : StoryEditorUiIntent()

    data object OpenProviderSettings : StoryEditorUiIntent()

    data object Resume : StoryEditorUiIntent()

    data object DismissDialog : StoryEditorUiIntent()
}
