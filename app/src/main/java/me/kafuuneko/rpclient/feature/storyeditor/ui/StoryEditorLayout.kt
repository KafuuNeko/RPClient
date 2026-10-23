package me.kafuuneko.rpclient.feature.storyeditor.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDestination
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryStructureTitleTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorDialogState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorStructureState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorTopBarState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.StoryChapterDestinationOption
import me.kafuuneko.rpclient.ui.dialog.StoryMoveChapterDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 分卷/章节故事编辑器及 Story 设置的 Compose 入口。 */
@Composable
fun StoryEditorLayout(
    uiState: StoryEditorUiState,
    document: StoryEditorDocument?,
    background: PageBackgroundUiState = PageBackgroundUiState(),
    emit: StoryEditorUiIntent.() -> Unit,
) {
    when (uiState) {
        StoryEditorUiState.None -> EditorLoading()
        is StoryEditorUiState.Normal -> StoryEditorNormal(
            state = uiState,
            document = document,
            background = background,
            emit = emit,
        )

        is StoryEditorUiState.Finished -> StoryEditorLayout(
            uiState = uiState.previous,
            document = document,
            background = background,
            emit = {},
        )
    }
}

@Composable
private fun StoryEditorNormal(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    background: PageBackgroundUiState,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    BackHandler(
        onBack = {
            when (state.pageState) {
                StoryEditorPageState.Editor -> StoryEditorUiIntent.Back.emit()
                StoryEditorPageState.Outline -> StoryEditorUiIntent.CloseStoryOutline.emit()
                StoryEditorPageState.LoadingSettings,
                is StoryEditorPageState.Settings -> StoryEditorUiIntent.CloseStorySettings.emit()
            }
        },
    )
    // 章节、大纲及设置都属于当前故事，加载设置时也保留同一张背景。
    PageBackdrop(
        state = background,
        useContainerAppearance = state.pageState != StoryEditorPageState.Editor
    ) {
        when (val pageState = state.pageState) {
            StoryEditorPageState.Editor -> StoryEditorNormalPage(
                state = state,
                document = document,
                emit = emit
            )

            StoryEditorPageState.Outline -> StoryOutlinePage(
                storyTitle = state.topBarState.title,
                structureState = state.structureState,
                emit = emit,
            )

            StoryEditorPageState.LoadingSettings -> StorySettingsLoadingPage(emit)
            is StoryEditorPageState.Settings -> StorySettingsPage(pageState, emit)
        }
    }
    EditorDialogSwitch(state, emit)
}

@Composable
private fun EditorDialogSwitch(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    when (val dialogState = state.dialogState) {
        StoryEditorDialogState.None -> Unit
        StoryEditorDialogState.SummaryTokenLimit -> AppConfirmDialog(
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.summary_token_limit_title),
            message = stringResource(
                R.string.summary_token_limit_message,
                stringResource(R.string.summary_memory),
                stringResource(R.string.general_summary_memory),
                stringResource(R.string.summary_response_tokens),
            ),
            confirmText = stringResource(R.string.summary_go_to_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { StoryEditorUiIntent.OpenSummarySettings.emit() },
        )

        is StoryEditorDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { StoryEditorUiIntent.OpenProviderSettings.emit() },
        )

        is StoryEditorDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            onCopyRequest = { StoryEditorUiIntent.CopyPromptItem(it).emit() },
        )

        StoryEditorDialogState.FileActions -> FileActionsDialog(emit)
        is StoryEditorDialogState.ImportPreview -> ImportPreviewDialog(dialogState, emit)
        is StoryEditorDialogState.StructureTitleEditor -> {
            val dialogTitle = when (dialogState.target) {
                StoryStructureTitleTarget.NewVolume -> stringResource(R.string.story_add_volume)
                is StoryStructureTitleTarget.NewChapter -> stringResource(R.string.story_add_chapter)
                is StoryStructureTitleTarget.Volume,
                is StoryStructureTitleTarget.Chapter -> stringResource(R.string.rename)
            }
            AppInputDialog(
                onDismissRequest = {
                    if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
                },
                title = dialogTitle,
                value = dialogState.title,
                onValueChange = { StoryEditorUiIntent.ChangeStructureTitle(it).emit() },
                label = stringResource(R.string.story_structure_title),
                enabled = !dialogState.isSaving,
                confirmText = stringResource(R.string.confirm),
                dismissText = stringResource(R.string.cancel),
                confirmEnabled = dialogState.title.isNotBlank() && !dialogState.isSaving,
                isConfirmLoading = dialogState.isSaving,
                onConfirm = { StoryEditorUiIntent.ConfirmStructureTitle.emit() },
            )
        }

        is StoryEditorDialogState.DeleteVolume -> AppDangerDialog(
            onDismissRequest = {
                if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
            },
            title = stringResource(R.string.story_delete_volume),
            message = stringResource(R.string.story_delete_volume_message, dialogState.title),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = !dialogState.isSaving,
            isConfirmLoading = dialogState.isSaving,
            onConfirm = { StoryEditorUiIntent.ConfirmDeleteVolume.emit() },
        )

        is StoryEditorDialogState.DeleteChapter -> AppDangerDialog(
            onDismissRequest = {
                if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
            },
            title = stringResource(R.string.story_delete_chapter),
            message = stringResource(R.string.story_delete_chapter_message, dialogState.title),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = !dialogState.isSaving,
            isConfirmLoading = dialogState.isSaving,
            onConfirm = { StoryEditorUiIntent.ConfirmDeleteChapter.emit() },
        )

        is StoryEditorDialogState.MoveChapter -> {
            val selectedVolumeId = when (val destination = dialogState.selectedDestination) {
                StoryChapterDestination.Ungrouped -> null
                is StoryChapterDestination.Volume -> destination.volumeId
            }
            StoryMoveChapterDialog(
                onDismissRequest = {
                    if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
                },
                chapterTitle = dialogState.title,
                options = buildList {
                    add(
                        element = StoryChapterDestinationOption(
                            volumeId = null,
                            title = stringResource(R.string.story_ungrouped_chapters),
                            chapterCount = state.structureState.ungroupedChapters.size,
                        ),
                    )
                    state.structureState.volumes.forEach { volume ->
                        add(
                            element = StoryChapterDestinationOption(
                                volumeId = volume.id,
                                title = volume.title,
                                chapterCount = volume.chapters.size,
                            ),
                        )
                    }
                },
                selectedVolumeId = selectedVolumeId,
                isSaving = dialogState.isSaving,
                onDestinationSelected = { volumeId ->
                    StoryEditorUiIntent.SelectChapterDestination(
                        destination = volumeId?.let(StoryChapterDestination::Volume)
                            ?: StoryChapterDestination.Ungrouped,
                    ).emit()
                },
                onConfirm = { StoryEditorUiIntent.ConfirmMoveStoryChapter.emit() },
            )
        }

        StoryEditorDialogState.SummarizingStory -> LoadingDialog(
            title = stringResource(R.string.story_summarizing),
            description = stringResource(R.string.story_summary_desc),
            onCancel = { StoryEditorUiIntent.CancelStorySummary.emit() },
        )

        is StoryEditorDialogState.StorySummaryPreview -> StorySummaryPreviewDialog(
            state = dialogState,
            emit = emit,
        )
    }
}

@Composable
private fun StorySummaryPreviewDialog(
    state: StoryEditorDialogState.StorySummaryPreview,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    AppDialogScaffold(
        onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.story_summary_preview),
        badgeIcon = Icons.Rounded.Description,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { StoryEditorUiIntent.ConfirmStorySummary.emit() },
    ) {
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.content,
            onValueChange = {},
            minLines = 5,
            maxLines = 12,
            readOnly = true,
            outputTransformation = rememberPromptMacroOutputTransformation(),
            shape = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun FileActionsDialog(emit: StoryEditorUiIntent.() -> Unit) {
    AppActionListDialog(
        onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.story_file_actions),
        badgeIcon = Icons.Rounded.FolderOpen,
        badgeTone = DialogBadgeTone.Primary,
        actions = listOf(
            AppActionItem(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.story_import_text),
                onClick = { StoryEditorUiIntent.ImportTextClick.emit() },
            ),
            AppActionItem(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.story_import_archive),
                onClick = { StoryEditorUiIntent.ImportStoryClick.emit() },
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_txt),
                onClick = {
                    StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Text).emit()
                },
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_markdown),
                onClick = {
                    StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Markdown).emit()
                },
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_archive),
                onClick = { StoryEditorUiIntent.ExportStoryClick.emit() },
            ),
        ),
    )
}

@Composable
private fun ImportPreviewDialog(
    state: StoryEditorDialogState.ImportPreview,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val preview = state.preview
    AppDialogScaffold(
        onDismissRequest = {
            if (!preview.isSaving) StoryEditorUiIntent.DismissDialog.emit()
        },
        title = stringResource(R.string.story_import_preview),
        badgeIcon = Icons.Rounded.FileDownload,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = stringResource(R.string.story_import_create),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = preview.title.isNotBlank() && !preview.isSaving,
        isConfirmLoading = preview.isSaving,
        onConfirm = { StoryEditorUiIntent.ConfirmImport.emit() },
        onDismiss = {
            if (!preview.isSaving) StoryEditorUiIntent.DismissDialog.emit()
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .generalContentBackground(shape = RoundedCornerShape(14.dp), hasLabel = true),
                value = preview.title,
                onValueChange = { text -> StoryEditorUiIntent.ChangeImportTitle(text).emit() },
                label = { Text(stringResource(R.string.story_title)) },
                singleLine = true,
                enabled = !preview.isSaving,
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = stringResource(
                    R.string.story_import_summary,
                    preview.draft.totalCharacterCount,
                    preview.draft.chapterCount,
                    preview.draft.characterHints.size,
                    preview.draft.lorebookHints.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 渲染故事初始加载与章节文档加载共用的占位内容。
 */
@Composable
internal fun EditorLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StoryEditorLayoutPreview() {
    AppTheme(dynamicColor = false) {
        StoryEditorLayout(
            uiState = StoryEditorUiState.Normal(
                storyId = 1L,
                topBarState = StoryEditorTopBarState(title = "Rain over the old city"),
                contentState = StoryEditorContentState(characterCount = 128),
                structureState = StoryEditorStructureState(
                    currentChapterId = 1L,
                    currentChapterTitle = "Chapter One",
                    ungroupedChapters = listOf(
                        element = StoryChapterOutlineItem(
                            id = 1L,
                            title = "Chapter One",
                            volumeId = null,
                            characterCount = 128,
                            sortOrder = 0,
                        ),
                    ),
                ),
                referenceState = StoryEditorReferenceState(
                    hasMemory = true,
                    hasAuthorNote = true,
                    characterCount = 2,
                    lorebookEntryCount = 4,
                ),
            ),
            document = StoryEditorDocument(
                storyId = 1L,
                chapterId = 1L,
                content = "# Chapter One\n\nRain tapped softly against the station windows.",
                syncVersion = 1L,
            ),
            emit = {},
        )
    }
}

@Preview(widthDp = 844, heightDp = 390, showBackground = true)
@Composable
private fun StoryEditorLayoutLandscapePreview() {
    StoryEditorLayoutPreview()
}

@Preview(widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun StoryEditorLayoutTabletLandscapePreview() {
    StoryEditorLayoutPreview()
}
