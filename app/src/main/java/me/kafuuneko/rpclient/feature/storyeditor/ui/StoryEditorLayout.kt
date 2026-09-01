package me.kafuuneko.rpclient.feature.storyeditor.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDestination
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropPosition
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterDropTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryChapterOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookEntryItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryStructureTitleTarget
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryTextExportFormat
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryVolumeOutlineItem
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryContinuationInputState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorDialogState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorStructureState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorTopBarState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationFailure
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationPhase
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySaveState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySettingsSection
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
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.DraggableItem
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.StoryUserPersonaCard
import me.kafuuneko.rpclient.ui.widgets.dragContainer
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.rememberLazyListDragDropState
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation
import androidx.compose.ui.platform.LocalLocale

/** 分卷/章节故事编辑器及 Story 设置的 Compose 入口。 */
@Composable
fun StoryEditorLayout(
    uiState: StoryEditorUiState,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (uiState) {
        StoryEditorUiState.None -> EditorLoading()
        is StoryEditorUiState.Normal -> StoryEditorNormal(uiState, document, emit)
        is StoryEditorUiState.Finished -> StoryEditorLayout(uiState.previous, document) {}
    }
}

@Composable
private fun StoryEditorNormal(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    BackHandler {
        when (state.pageState) {
            StoryEditorPageState.Editor -> StoryEditorUiIntent.Back.emit()
            StoryEditorPageState.Outline -> StoryEditorUiIntent.CloseStoryOutline.emit()
            StoryEditorPageState.LoadingSettings,
            is StoryEditorPageState.Settings -> StoryEditorUiIntent.CloseStorySettings.emit()
        }
    }
    when (val pageState = state.pageState) {
        StoryEditorPageState.Editor -> StoryEditorPage(state, document, emit)
        StoryEditorPageState.Outline -> StoryOutlinePage(
            storyTitle = state.topBarState.title,
            structureState = state.structureState,
            emit = emit
        )

        StoryEditorPageState.LoadingSettings -> StorySettingsLoadingPage(emit)
        is StoryEditorPageState.Settings -> StorySettingsPage(pageState, emit)
    }
    EditorDialogSwitch(state, emit)
}

@Composable
private fun StoryEditorPage(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 编辑状态位于自适应布局分支之外，窗口尺寸切换时继续复用当前草稿与光标。
    val editorState = remember(document?.chapterId) {
        TextFieldState(document?.content.orEmpty())
    }
    val documentMatchesChapter = document?.chapterId == state.structureState.currentChapterId
    val onContinue = document?.takeIf { documentMatchesChapter }?.let { currentDocument ->
        {
            StoryEditorUiIntent.ContinueStory(
                StoryEditorSnapshot(
                    chapterId = currentDocument.chapterId,
                    content = editorState.text.toString(),
                    isComposing = editorState.composition != null
                )
            ).emit()
        }
    }

    // 窗口等级不随 IME 开合变化，避免输入时切换布局并打断焦点。
    val layoutMode = storyEditorLayoutMode(currentWindowAdaptiveInfo().windowSizeClass)
    StoryEditorScaffold(
        state = state,
        document = document,
        editorState = editorState,
        documentMatchesChapter = documentMatchesChapter,
        layoutMode = layoutMode,
        onContinue = onContinue,
        emit = emit
    )
}

/** 按窗口约束组织故事编辑器的常规、横屏沉浸或紧凑布局。 */
@Composable
private fun StoryEditorScaffold(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    layoutMode: StoryEditorLayoutMode,
    onContinue: (() -> Unit)?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 手机横屏模式由左侧导航轨承载，Scaffold 顶栏隐藏以释放纵向空间
            if (layoutMode != StoryEditorLayoutMode.CompactWide) {
                StoryEditorTopBar(state, emit)
            }
        },
        bottomBar = {
            if (layoutMode == StoryEditorLayoutMode.Regular) {
                EditorBottomBar(
                    characterCount = state.contentState.characterCount,
                    referenceState = state.referenceState,
                    contentState = state.contentState,
                    continuationInputState = state.continuationInputState,
                    generationState = state.generationState,
                    canUndoEdit = state.canUndoEdit,
                    canRedoEdit = state.canRedoEdit,
                    onContinue = onContinue,
                    applyBottomInsets = true,
                    emit = emit
                )
            }
        }
    ) { paddingValues ->
        when (layoutMode) {
            StoryEditorLayoutMode.Regular -> StoryEditorRegularContent(
                state = state,
                document = document,
                editorState = editorState,
                documentMatchesChapter = documentMatchesChapter,
                paddingValues = paddingValues,
                emit = emit
            )

            StoryEditorLayoutMode.CompactWide -> StoryEditorLandscapeContent(
                state = state,
                document = document,
                editorState = editorState,
                documentMatchesChapter = documentMatchesChapter,
                paddingValues = paddingValues,
                onContinue = onContinue,
                emit = emit
            )

            StoryEditorLayoutMode.CompactNarrow -> StoryEditorCompactContent(
                state = state,
                document = document,
                editorState = editorState,
                documentMatchesChapter = documentMatchesChapter,
                paddingValues = paddingValues,
                onContinue = onContinue,
                showStatistics = false,
                emit = emit
            )
        }
    }
}

/** 渲染故事编辑器顶栏及页面级操作。 */
@Composable
private fun StoryEditorTopBar(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit
) {
    AppTopBar(
        title = state.topBarState.title,
        onBack = { StoryEditorUiIntent.Back.emit() },
        actions = {
            // 保存状态常驻顶栏，窗口紧凑时仍能看到草稿是否安全落库。
            SaveStatus(state.topBarState.saveState, emit)
            IconButton(
                onClick = { StoryEditorUiIntent.OpenPromptInspector.emit() },
                enabled = state.hasPromptInspection
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.prompt_inspector_title)
                )
            }
            IconButton(
                onClick = { StoryEditorUiIntent.OpenFileActions.emit() },
                enabled = state.generationState is StoryGenerationState.Idle
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = stringResource(R.string.story_file_actions)
                )
            }
            IconButton(
                onClick = { StoryEditorUiIntent.OpenStorySettings.emit() },
                enabled = state.generationState is StoryGenerationState.Idle
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.story_settings)
                )
            }
        }
    )
}

/** 使用原有纵向结构渲染中等及以上高度窗口。 */
@Composable
private fun StoryEditorRegularContent(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    paddingValues: PaddingValues,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 常规高度保留完整问题提示，避免改变竖屏下的信息层级。
        StoryEditorProblemBanners(state, emit)
        StoryEditorMainPane(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 760.dp),
            state = state,
            document = document,
            editorState = editorState,
            documentMatchesChapter = documentMatchesChapter,
            compactChapterBar = false,
            showChapterBar = true,
            emit = emit
        )
    }
}

/**
 * 专为手机横屏与紧凑宽屏环境构建的双翼环抱式沉浸布局。
 *
 * 核心人机工程与视觉设计：
 * - 左右双翼触控：左手掌管大纲与全局导航，右手拇指处于高频续写与历史撤销热区。
 * - 去除顶部全局栏：将全局导航与辅助入口收纳至左侧导航轨，彻底释放纵向高度。
 * - 沉浸稿纸排版：正文直通全屏，配合轻量内联章节条与流式生成微光晕反馈。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryEditorLandscapeContent(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    paddingValues: PaddingValues,
    onContinue: (() -> Unit)?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val imeVisible = WindowInsets.isImeVisible
    var isGuidanceExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧极简导航轨
        StoryEditorLeftRail(
            state = state,
            document = document,
            editorState = editorState,
            documentMatchesChapter = documentMatchesChapter,
            compactMode = imeVisible,
            emit = emit
        )

        // 中央沉浸稿纸区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // IME 打开后优先保留正文和正在输入的工具栏，避免提示再次耗尽纵向空间
            if (!imeVisible) {
                StoryEditorProblemBanners(state, emit)
            }
            StoryEditorLandscapeCenterPane(
                modifier = Modifier.weight(1f),
                state = state,
                document = document,
                editorState = editorState,
                documentMatchesChapter = documentMatchesChapter,
                isGuidanceExpanded = isGuidanceExpanded,
                onContinue = onContinue,
                emit = emit
            )
        }

        // 右侧拇指操作岛
        StoryEditorRightDock(
            characterCount = state.contentState.characterCount,
            contentState = state.contentState,
            continuationInputState = state.continuationInputState,
            generationState = state.generationState,
            canUndoEdit = state.canUndoEdit,
            canRedoEdit = state.canRedoEdit,
            isGuidanceExpanded = isGuidanceExpanded,
            onToggleGuidance = { isGuidanceExpanded = !isGuidanceExpanded },
            onContinue = onContinue,
            compactMode = imeVisible,
            emit = emit
        )
    }
}

/**
 * 横屏左侧极简导航轨，承载全局返回、大纲入口与关键配置能力。
 *
 * 核心交互：
 * - 顶部：返回上一页与保存状态微标。
 * - 中部：章节大纲入口与当前卷章信息。
 * - 底部：Prompt 检查器、导入导出与故事设置入口。
 */
@Composable
private fun StoryEditorLeftRail(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    compactMode: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .width(if (compactMode) 42.dp else 48.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：返回按钮与极简保存状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { StoryEditorUiIntent.Back.emit() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp)
                    )
                }
                SaveStatusMini(saveState = state.topBarState.saveState, emit = emit)
            }

            // 中部：分卷与大纲快速入口
            IconButton(
                onClick = {
                    document?.takeIf { documentMatchesChapter }?.let { currentDoc ->
                        StoryEditorUiIntent.OpenStoryOutline(
                            StoryEditorSnapshot(
                                chapterId = currentDoc.chapterId,
                                content = editorState.text.toString(),
                                isComposing = editorState.composition != null
                            )
                        ).emit()
                    }
                },
                enabled = documentMatchesChapter &&
                        state.contentState.editable &&
                        state.generationState is StoryGenerationState.Idle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = stringResource(R.string.story_outline),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 底部：检查器、导入导出、设置
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!compactMode) {
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenPromptInspector.emit() },
                        enabled = state.hasPromptInspection,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.prompt_inspector_title),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenFileActions.emit() },
                        enabled = state.generationState is StoryGenerationState.Idle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = stringResource(R.string.story_file_actions),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { StoryEditorUiIntent.OpenStorySettings.emit() },
                    enabled = state.generationState is StoryGenerationState.Idle,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.story_settings),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/**
 * 适用于横屏侧边栏的紧凑微标保存状态。
 */
@Composable
private fun SaveStatusMini(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val clickable = saveState == StorySaveState.Failed
    Surface(
        modifier = Modifier.clickable(enabled = clickable) {
            StoryEditorUiIntent.RetrySave.emit()
        },
        shape = CircleShape,
        color = when (saveState) {
            StorySaveState.Failed,
            StorySaveState.Conflict -> MaterialTheme.colorScheme.errorContainer

            StorySaveState.Dirty,
            StorySaveState.Saving -> MaterialTheme.colorScheme.secondaryContainer

            StorySaveState.Saved -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (saveState) {
                    StorySaveState.Saved -> Icons.Rounded.Check
                    StorySaveState.Dirty -> Icons.Rounded.Save
                    StorySaveState.Saving -> Icons.Rounded.HourglassTop
                    StorySaveState.Failed,
                    StorySaveState.Conflict -> Icons.Rounded.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = when (saveState) {
                    StorySaveState.Failed,
                    StorySaveState.Conflict -> MaterialTheme.colorScheme.onErrorContainer

                    StorySaveState.Dirty,
                    StorySaveState.Saving -> MaterialTheme.colorScheme.onSecondaryContainer

                    StorySaveState.Saved -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * 横屏右侧拇指操作岛，将高频续写、历史回退与灵感面板聚合于右手舒适热区。
 *
 * 核心排布：
 * - 顶部：全书/本章字数微型徽标与状态展示。
 * - 中部：灵感引导触发器与撤销/重做竖排按键。
 * - 底部：高对比度、大触控面积的 AI 续写主控按钮。
 */
@Composable
private fun StoryEditorRightDock(
    characterCount: Int,
    contentState: StoryEditorContentState,
    continuationInputState: StoryContinuationInputState,
    generationState: StoryGenerationState,
    canUndoEdit: Boolean,
    canRedoEdit: Boolean,
    isGuidanceExpanded: Boolean,
    onToggleGuidance: () -> Unit,
    onContinue: (() -> Unit)?,
    compactMode: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val historyEnabled = contentState.editable && generationState is StoryGenerationState.Idle

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .width(if (compactMode) 44.dp else 52.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部：字数微型指示
            if (!compactMode) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (characterCount >= 1000) {
                                String.format(LocalLocale.current.platformLocale, "%.1fk", characterCount / 1000f)
                            } else {
                                "$characterCount"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 中部：灵感引导折叠切换与撤销/重做
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 灵感引导展开按钮
                Surface(
                    modifier = Modifier.clickable(
                        enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleGuidance()
                        }
                    ),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGuidanceExpanded || continuationInputState.guidanceDraft.isNotBlank()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    }
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.story_continuation_guidance),
                            modifier = Modifier.size(16.dp),
                            tint = if (isGuidanceExpanded || continuationInputState.guidanceDraft.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        StoryEditorUiIntent.UndoLastEdit.emit()
                    },
                    enabled = historyEnabled && canUndoEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.story_undo),
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        StoryEditorUiIntent.RedoLastEdit.emit()
                    },
                    enabled = historyEnabled && canRedoEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = stringResource(R.string.story_redo),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // 底部：AI 续写大按键
            LandscapeGenerationAction(
                contentState = contentState,
                generationState = generationState,
                onContinue = onContinue,
                hapticFeedback = hapticFeedback,
                emit = emit
            )
        }
    }
}

/** 渲染横屏操作岛尾部的续写、停止或进行中状态。 */
@Composable
private fun LandscapeGenerationAction(
    contentState: StoryEditorContentState,
    generationState: StoryGenerationState,
    onContinue: (() -> Unit)?,
    hapticFeedback: HapticFeedback,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (generationState) {
        is StoryGenerationState.Streaming -> Button(
            modifier = Modifier.size(42.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                StoryEditorUiIntent.StopGeneration.emit()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.story_stop_generation),
                modifier = Modifier.size(20.dp)
            )
        }

        StoryGenerationState.Preparing,
        StoryGenerationState.Applying -> Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        else -> Button(
            modifier = Modifier.size(42.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onContinue?.invoke()
            },
            enabled = contentState.editable &&
                    generationState is StoryGenerationState.Idle &&
                    onContinue != null,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.story_continue),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 横屏中央沉浸正文容器，承载微型章节信息与全高正文编辑器。
 */
@Composable
private fun StoryEditorLandscapeCenterPane(
    modifier: Modifier,
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    isGuidanceExpanded: Boolean,
    onContinue: (() -> Unit)?,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 内联极简章节条
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // 当前章节名称微胶囊
            Surface(
                modifier = Modifier.clickable(
                    enabled = documentMatchesChapter &&
                            state.contentState.editable &&
                            state.generationState is StoryGenerationState.Idle,
                    onClick = {
                        document?.takeIf { documentMatchesChapter }?.let { currentDoc ->
                            StoryEditorUiIntent.OpenStoryOutline(
                                StoryEditorSnapshot(
                                    chapterId = currentDoc.chapterId,
                                    content = editorState.text.toString(),
                                    isComposing = editorState.composition != null
                                )
                            ).emit()
                        }
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = state.structureState.currentChapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.story_open_outline),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // 展开的引导输入框（如果展开）
        AnimatedVisibility(
            visible = isGuidanceExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.continuationInputState.guidanceDraft,
                onValueChange = { StoryEditorUiIntent.ChangeContinuationGuidance(it).emit() },
                enabled = state.contentState.editable && state.generationState is StoryGenerationState.Idle,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.story_continuation_guidance_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingIcon = {
                    if (state.continuationInputState.guidanceDraft.isNotBlank()) {
                        IconButton(
                            onClick = { StoryEditorUiIntent.ChangeContinuationGuidance("").emit() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.prompt_editor_clear),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )
        }

        // 正文编辑器
        if (document == null || !documentMatchesChapter) {
            EditorLoading()
        } else {
            StoryTextEditor(
                modifier = Modifier.weight(1f),
                document = document,
                editorState = editorState,
                editable = state.contentState.editable,
                generationState = state.generationState,
                emit = emit
            )
        }
    }
}

/** 在紧凑高度使用全宽正文与单行底部工具坞。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryEditorCompactContent(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    paddingValues: PaddingValues,
    onContinue: (() -> Unit)?,
    showStatistics: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val imeVisible = WindowInsets.isImeVisible
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // IME 打开后优先保留正文和正在输入的工具栏，避免提示再次耗尽纵向空间。
        if (!imeVisible) {
            StoryEditorProblemBanners(state, emit)
        }
        StoryEditorMainPane(
            modifier = Modifier.weight(1f),
            state = state,
            document = document,
            editorState = editorState,
            documentMatchesChapter = documentMatchesChapter,
            compactChapterBar = true,
            showChapterBar = !imeVisible,
            emit = emit
        )
        EditorCompactBottomBar(
            characterCount = state.contentState.characterCount,
            referenceState = state.referenceState,
            contentState = state.contentState,
            continuationInputState = state.continuationInputState,
            generationState = state.generationState,
            canUndoEdit = state.canUndoEdit,
            canRedoEdit = state.canRedoEdit,
            onContinue = onContinue,
            showStatistics = showStatistics,
            emit = emit
        )
    }
}

/** 渲染章节入口与正文，使两种自适应结构共享同一编辑器实现。 */
@Composable
private fun StoryEditorMainPane(
    modifier: Modifier,
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    editorState: TextFieldState,
    documentMatchesChapter: Boolean,
    compactChapterBar: Boolean,
    showChapterBar: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compactChapterBar) 6.dp else 10.dp)
    ) {
        // 键盘占用紧凑窗口时暂时收起章节入口，为正文保留稳定的可编辑高度。
        if (showChapterBar) {
            CurrentChapterBar(
                state = state.structureState,
                enabled = documentMatchesChapter &&
                        state.contentState.editable &&
                        state.generationState is StoryGenerationState.Idle,
                compact = compactChapterBar,
                onClick = document?.takeIf { documentMatchesChapter }?.let { currentDocument ->
                    {
                        StoryEditorUiIntent.OpenStoryOutline(
                            StoryEditorSnapshot(
                                chapterId = currentDocument.chapterId,
                                content = editorState.text.toString(),
                                isComposing = editorState.composition != null
                            )
                        ).emit()
                    }
                }
            )
        }
        // 文档切换期间保持加载态，避免短暂编辑到上一章节的文本实例。
        if (document == null || !documentMatchesChapter) {
            EditorLoading()
        } else {
            StoryTextEditor(
                modifier = Modifier.weight(1f),
                document = document,
                editorState = editorState,
                editable = state.contentState.editable,
                generationState = state.generationState,
                emit = emit
            )
        }
    }
}

/** 汇总保存、生成和模型配置问题提示。 */
@Composable
private fun StoryEditorProblemBanners(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit
) {
    SaveProblemBanner(state.topBarState.saveState, emit)
    GenerationProblemBanner(state.generationState, emit)
    if (!state.hasAvailableProvider) {
        NoProviderBanner(
            onClick = { StoryEditorUiIntent.OpenProviderSettings.emit() }
        )
    }
}

/** 根据当前窗口大小等级选择故事编辑器的结构。 */
private fun storyEditorLayoutMode(windowSizeClass: WindowSizeClass): StoryEditorLayoutMode = when {
    windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> {
        StoryEditorLayoutMode.Regular
    }
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
        StoryEditorLayoutMode.CompactWide
    }
    else -> StoryEditorLayoutMode.CompactNarrow
}

private enum class StoryEditorLayoutMode {
    Regular,
    CompactWide,
    CompactNarrow
}

@Composable
private fun CurrentChapterBar(
    state: StoryEditorStructureState,
    enabled: Boolean,
    compact: Boolean,
    onClick: (() -> Unit)?
) {
    // 外层容器采用 Secondary Container 柔和配色与微边框
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 章节主题图标指示
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            // 紧凑高度只保留章节标题，分卷信息可在大纲页继续查看。
            if (compact) {
                Text(
                    text = state.currentChapterTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = state.currentChapterTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.currentVolumeTitle
                            ?: stringResource(R.string.story_ungrouped_chapters),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 进入大纲的轻量胶囊按钮提示
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!compact) {
                        Text(
                            text = stringResource(R.string.story_outline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.story_open_outline),
                        modifier = Modifier.size(16.dp),
                        tint = if (enabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryTextEditor(
    modifier: Modifier,
    document: StoryEditorDocument,
    editorState: TextFieldState,
    editable: Boolean,
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 正文不使用 rememberSaveable，避免长文进入 Activity Bundle；Room 承担进程恢复。
    val editorScrollState = rememberScrollState()
    val followStreamingOutput = generationState is StoryGenerationState.Streaming
    val generatedTextColor = MaterialTheme.colorScheme.primary
    val isStreaming = generationState is StoryGenerationState.Streaming ||
            generationState is StoryGenerationState.Preparing ||
            generationState is StoryGenerationState.Applying

    // 流式生成中的呼吸光晕动效
    val infiniteTransition = rememberInfiniteTransition(label = "StoryStreamingGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    val displayedEditedRange = document.latestEditedRange.takeUnless {
        generationState is StoryGenerationState.Streaming ||
                generationState is StoryGenerationState.Applying
    }
    val currentEditedRange by rememberUpdatedState(displayedEditedRange)
    val currentEditedStyle by rememberUpdatedState(
        SpanStyle(
            color = generatedTextColor,
            background = generatedTextColor.copy(alpha = 0.12f),
            fontWeight = FontWeight.Medium
        )
    )
    // 转换实例必须跨流式分片保持稳定，否则 BasicTextField 会反复重建布局状态并闪烁。
    val latestEditedOutputTransformation = remember {
        OutputTransformation {
            val editedRange = currentEditedRange
            if (
                editedRange != null &&
                editedRange.start < editedRange.end &&
                editedRange.end <= length
            ) {
                addStyle(
                    spanStyle = currentEditedStyle,
                    start = editedRange.start,
                    end = editedRange.end
                )
            }
        }
    }
    LaunchedEffect(document.syncVersion) {
        if (editorState.text.toString() != document.content) {
            editorState.syncTextPreservingSelection(document.content)
        }
    }
    LaunchedEffect(followStreamingOutput, editorScrollState) {
        if (!followStreamingOutput) return@LaunchedEffect
        // 流式文本布局完成后 maxValue 会持续增长，跟随它才能稳定滚到最新输出。
        snapshotFlow { editorScrollState.maxValue }.collect { bottom ->
            editorScrollState.scrollTo(bottom)
        }
    }
    LaunchedEffect(editorState) {
        snapshotFlow {
            StoryEditorSnapshot(
                chapterId = document.chapterId,
                content = editorState.text.toString(),
                isComposing = editorState.composition != null
            )
        }.distinctUntilChanged().collect { snapshot ->
            StoryEditorUiIntent.EditorSnapshotChanged(snapshot).emit()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (isStreaming) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (isStreaming) 1.5.dp else 1.dp,
            color = if (isStreaming) {
                MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        BasicTextField(
            state = editorState,
            modifier = Modifier
                .fillMaxSize()
                .draggableScrollIndicator(editorScrollState),
            enabled = editable,
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp
                )
            ),
            scrollState = editorScrollState,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            outputTransformation = latestEditedOutputTransformation,
            decorator = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    if (editorState.text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.story_editor_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun SaveStatus(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val clickable = saveState == StorySaveState.Failed
    Surface(
        modifier = Modifier.clickable(enabled = clickable) {
            StoryEditorUiIntent.RetrySave.emit()
        },
        shape = RoundedCornerShape(50),
        color = when (saveState) {
            StorySaveState.Failed,
            StorySaveState.Conflict -> MaterialTheme.colorScheme.errorContainer

            StorySaveState.Dirty,
            StorySaveState.Saving -> MaterialTheme.colorScheme.secondaryContainer

            StorySaveState.Saved -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (saveState) {
                    StorySaveState.Saved -> Icons.Rounded.Check
                    StorySaveState.Dirty -> Icons.Rounded.Save
                    StorySaveState.Saving -> Icons.Rounded.HourglassTop
                    StorySaveState.Failed,
                    StorySaveState.Conflict -> Icons.Rounded.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(
                    when (saveState) {
                        StorySaveState.Saved -> R.string.story_saved
                        StorySaveState.Dirty -> R.string.story_unsaved
                        StorySaveState.Saving -> R.string.story_saving
                        StorySaveState.Failed -> R.string.story_save_failed_short
                        StorySaveState.Conflict -> R.string.story_save_conflict_short
                    }
                ),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun SaveProblemBanner(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (saveState) {
        StorySaveState.Failed -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.story_save_failed_message),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { StoryEditorUiIntent.RetrySave.emit() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        StorySaveState.Conflict -> Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.story_save_conflict_message),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { StoryEditorUiIntent.CopyConflictDraft.emit() }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_copy_draft))
                    }
                    TextButton(onClick = { StoryEditorUiIntent.ReloadAfterConflict.emit() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_reload_saved))
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun GenerationProblemBanner(
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val failure = generationState as? StoryGenerationState.Failed ?: return
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    when (failure.reason) {
                        StoryGenerationFailure.Setup -> R.string.story_generation_failed
                        StoryGenerationFailure.Provider -> {
                            if (failure.recoverablePartial.isBlank()) {
                                R.string.story_generation_provider_failed_without_partial
                            } else {
                                R.string.story_generation_provider_failed
                            }
                        }

                        StoryGenerationFailure.ApplyResult -> {
                            R.string.story_generation_apply_failed
                        }

                        StoryGenerationFailure.Conflict -> R.string.story_generation_conflict
                        StoryGenerationFailure.EmptyResult -> R.string.story_generation_empty
                        StoryGenerationFailure.ContextBudget -> R.string.story_generation_budget
                    }
                ),
                style = MaterialTheme.typography.bodySmall
            )
            if (failure.detail.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.story_generation_largest_characters,
                        failure.detail
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (failure.recoverablePartial.isNotBlank()) {
                    TextButton(onClick = { StoryEditorUiIntent.CopyRecoverablePartial.emit() }) {
                        Text(stringResource(R.string.copy))
                    }
                    TextButton(onClick = { StoryEditorUiIntent.InsertRecoverablePartial.emit() }) {
                        Text(stringResource(R.string.story_insert_partial))
                    }
                }
                TextButton(onClick = { StoryEditorUiIntent.DiscardRecoverablePartial.emit() }) {
                    Text(
                        stringResource(
                            if (failure.recoverablePartial.isBlank()) {
                                R.string.close
                            } else {
                                R.string.story_discard
                            }
                        )
                    )
                }
            }
        }
    }
}

/** 在紧凑高度用单行工具坞保留续写与编辑历史操作。 */
@Composable
private fun EditorCompactBottomBar(
    characterCount: Int,
    referenceState: StoryEditorReferenceState,
    contentState: StoryEditorContentState,
    continuationInputState: StoryContinuationInputState,
    generationState: StoryGenerationState,
    canUndoEdit: Boolean,
    canRedoEdit: Boolean,
    onContinue: (() -> Unit)?,
    showStatistics: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val historyEnabled = contentState.editable && generationState is StoryGenerationState.Idle
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 引导输入压缩为单行，避免紧凑窗口继续堆叠两层底部控件。
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = continuationInputState.guidanceDraft,
                onValueChange = { StoryEditorUiIntent.ChangeContinuationGuidance(it).emit() },
                enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.story_continuation_guidance),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                shape = RoundedCornerShape(14.dp)
            )
            if (showStatistics) {
                CompactEditorStatistics(
                    characterCount = characterCount,
                    referenceState = referenceState
                )
            }
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StoryEditorUiIntent.UndoLastEdit.emit()
                },
                enabled = historyEnabled && canUndoEdit
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.story_undo)
                )
            }
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StoryEditorUiIntent.RedoLastEdit.emit()
                },
                enabled = historyEnabled && canRedoEdit
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.story_redo)
                )
            }
            // 生成中的紧凑按钮与常规底栏保持相同的停止和禁用语义。
            CompactGenerationAction(
                contentState = contentState,
                generationState = generationState,
                onContinue = onContinue,
                hapticFeedback = hapticFeedback,
                emit = emit
            )
        }
    }
}

/** 在宽屏紧凑工具栏中展示正文与引用规模。 */
@Composable
private fun CompactEditorStatistics(
    characterCount: Int,
    referenceState: StoryEditorReferenceState
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // 统计保持双行紧凑结构，不与续写输入争夺主要宽度。
        Text(
            text = stringResource(R.string.story_character_count, characterCount),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = stringResource(
                        R.string.story_character_references_count,
                        referenceState.characterCount
                    ),
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${referenceState.characterCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Book,
                    contentDescription = stringResource(
                        R.string.story_lorebook_entries_count,
                        referenceState.lorebookEntryCount
                    ),
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${referenceState.lorebookEntryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 渲染紧凑工具栏尾部的续写、停止或进行中状态。 */
@Composable
private fun CompactGenerationAction(
    contentState: StoryEditorContentState,
    generationState: StoryGenerationState,
    onContinue: (() -> Unit)?,
    hapticFeedback: HapticFeedback,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 状态分支只改变尾部动作，紧凑工具栏的输入与历史操作始终保持稳定位置。
    when (generationState) {
        is StoryGenerationState.Streaming -> Button(
            modifier = Modifier.size(48.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                StoryEditorUiIntent.StopGeneration.emit()
            },
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.story_stop_generation)
            )
        }

        StoryGenerationState.Preparing,
        StoryGenerationState.Applying -> Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp
            )
        }

        else -> Button(
            modifier = Modifier.size(48.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onContinue?.invoke()
            },
            enabled = contentState.editable &&
                    generationState is StoryGenerationState.Idle &&
                    onContinue != null,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.story_continue)
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    modifier: Modifier = Modifier,
    characterCount: Int,
    referenceState: StoryEditorReferenceState,
    contentState: StoryEditorContentState,
    continuationInputState: StoryContinuationInputState,
    generationState: StoryGenerationState,
    canUndoEdit: Boolean,
    canRedoEdit: Boolean,
    onContinue: (() -> Unit)?,
    applyBottomInsets: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applyBottomInsets) {
                        // 导航栏与 IME 分别声明，避免支持窗格把键盘高度计入自身尺寸。
                        Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    } else {
                        Modifier
                    }
                )
        ) {
            RpScrollableOutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                value = continuationInputState.guidanceDraft,
                onValueChange = { StoryEditorUiIntent.ChangeContinuationGuidance(it).emit() },
                enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                minLines = 1,
                maxLines = 4,
                label = { Text(stringResource(R.string.story_continuation_guidance)) },
                placeholder = { Text(stringResource(R.string.story_continuation_guidance_placeholder)) },
                shape = RoundedCornerShape(16.dp)
            )
            when (generationState) {
                StoryGenerationState.Preparing,
                is StoryGenerationState.Streaming,
                StoryGenerationState.Applying -> StoryGenerationStatusCard(
                    generationState = generationState,
                    emit = emit
                )
                else -> Unit
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = stringResource(R.string.story_character_count, characterCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = stringResource(
                                    R.string.story_character_references_count,
                                    referenceState.characterCount
                                ),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${referenceState.characterCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Book,
                                contentDescription = stringResource(
                                    R.string.story_lorebook_entries_count,
                                    referenceState.lorebookEntryCount
                                ),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${referenceState.lorebookEntryCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                when (generationState) {
                    is StoryGenerationState.Streaming -> Button(
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            StoryEditorUiIntent.StopGeneration.emit()
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_stop_generation))
                    }

                    StoryGenerationState.Preparing,
                    StoryGenerationState.Applying -> Unit

                    else -> {
                        val historyEnabled = contentState.editable &&
                                generationState is StoryGenerationState.Idle
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.UndoLastEdit.emit()
                            },
                            enabled = historyEnabled && canUndoEdit
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.story_undo)
                            )
                        }
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.RedoLastEdit.emit()
                            },
                            enabled = historyEnabled && canRedoEdit
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = stringResource(R.string.story_redo)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onContinue?.invoke()
                            },
                            enabled = contentState.editable &&
                                    generationState is StoryGenerationState.Idle &&
                                    onContinue != null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.story_continue))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 在编辑器底栏展示由真实请求事件驱动的 AI 写作状态。
 *
 * 推理详情默认折叠，正文开始后仍可主动回看；卡片只展示 ViewModel 提供的受限
 * 文本，不自行解析或持久化模型响应。
 */
@Composable
private fun StoryGenerationStatusCard(
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val streaming = generationState as? StoryGenerationState.Streaming
    val elapsedSeconds = streaming?.let {
        rememberGenerationElapsedSeconds(it.startedAtElapsedRealtime)
    }
    val hasReasoning = streaming?.reasoningPreview?.isNotBlank() == true
    val isExpanded = streaming?.isReasoningExpanded == true
    val hapticFeedback = LocalHapticFeedback.current
    val statusTitle = generationState.statusTitle()
    val statusSubtitle = generationState.statusSubtitle(elapsedSeconds ?: 0L)
    // 仅在确实收到推理内容后允许展开，避免无反馈的伪交互
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
        .clickable(enabled = hasReasoning) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            StoryEditorUiIntent.ToggleGenerationReasoning.emit()
        }
    Surface(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题区保持紧凑，在小屏上优先压缩状态文本而不是操作图标
            StoryGenerationStatusHeader(
                title = statusTitle,
                subtitle = statusSubtitle,
                elapsedSeconds = elapsedSeconds,
                hasReasoning = hasReasoning,
                isExpanded = isExpanded
            )
            // 推理详情限制最大高度，避免长构思挤占整个编辑器
            StoryGenerationReasoningDetail(
                reasoningDetail = streaming?.reasoningDetail.orEmpty(),
                visible = isExpanded
            )
        }
    }
}

/** 状态卡的标题、耗时和展开指示区。 */
@Composable
private fun StoryGenerationStatusHeader(
    title: String,
    subtitle: String,
    elapsedSeconds: Long?,
    hasReasoning: Boolean,
    isExpanded: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 图标与文字沿用故事页的圆角、弱强调信息密度
        StoryGenerationStatusIcon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                    maxLines = if (isExpanded) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 耗时和展开箭头固定在尾部，状态标题在窄屏下优先让出空间
        elapsedSeconds?.let { StoryGenerationElapsedBadge(it) }
        if (hasReasoning) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = stringResource(
                    if (isExpanded) R.string.hide else R.string.show
                ),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 展开显示受限高度的模型构思详情。 */
@Composable
private fun StoryGenerationReasoningDetail(
    reasoningDetail: String,
    visible: Boolean
) {
    val scrollState = rememberScrollState()
    val isUserDragging by scrollState.interactionSource.collectIsDraggedAsState()
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var manualScrollInProgress by remember { mutableStateOf(false) }
    // 用户开始拖动时立即暂停自动跟随，避免新到达的 token 与手势争夺滚动位置。
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            manualScrollInProgress = true
            shouldFollowBottom = false
        }
    }
    // 等待拖动及其惯性滚动完全结束，再依据最终位置决定是否恢复自动跟随。
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling && manualScrollInProgress) {
                    shouldFollowBottom = scrollState.isAtBottom()
                    manualScrollInProgress = false
                }
            }
    }
    // 仅在保持底部跟随时监听内容高度变化；collectLatest 可及时追上快速流式增量。
    LaunchedEffect(scrollState, visible, shouldFollowBottom) {
        if (!visible || !shouldFollowBottom) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }
            .distinctUntilChanged()
            .collectLatest { bottom -> scrollState.animateScrollTo(bottom) }
    }
    AnimatedVisibility(
        visible = visible && reasoningDetail.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        // 内层 Surface 将模型文本与可操作的外层状态卡作视觉分组
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            border = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Text(
                text = reasoningDetail,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .draggableScrollIndicator(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 是否已经位于推理详情底部；保留少量像素容差以吸收布局取整。 */
private fun ScrollState.isAtBottom(): Boolean {
    return maxValue - value <= REASONING_SCROLL_BOTTOM_TOLERANCE_PX
}

/** 状态卡左侧的轻量动态标识。 */
@Composable
private fun StoryGenerationStatusIcon() {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(34.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** 用弱强调胶囊展示当前生成任务已经持续的秒数。 */
@Composable
private fun StoryGenerationElapsedBadge(elapsedSeconds: Long) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
    ) {
        Text(
            text = stringResource(R.string.story_generation_elapsed_seconds, elapsedSeconds),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 以单调时钟刷新纯展示用耗时，组合离开后协程会自动取消。 */
@Composable
private fun rememberGenerationElapsedSeconds(startedAtElapsedRealtime: Long): Long {
    var elapsedSeconds by remember(startedAtElapsedRealtime) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAtElapsedRealtime) {
        while (true) {
            elapsedSeconds = (
                SystemClock.elapsedRealtime() - startedAtElapsedRealtime
            ).coerceAtLeast(0L) / 1_000L
            delay(1_000L)
        }
    }
    return elapsedSeconds
}

/** 将生成状态映射为底栏使用的本地化主标题。 */
@Composable
private fun StoryGenerationState.statusTitle(): String {
    val resId = when (this) {
        StoryGenerationState.Preparing -> R.string.story_generation_status_preparing
        is StoryGenerationState.Streaming -> when (phase) {
            StoryGenerationPhase.AwaitingResponse -> R.string.story_generation_status_waiting
            StoryGenerationPhase.Connected -> R.string.story_generation_status_connected
            StoryGenerationPhase.Reasoning -> R.string.story_generation_status_reasoning
            StoryGenerationPhase.Writing -> R.string.story_generation_status_writing
        }
        StoryGenerationState.Applying -> R.string.story_generation_status_applying
        else -> return ""
    }
    return stringResource(resId)
}

/** 根据真实阶段提供短提示，长时间首字等待时给出可停止说明。 */
@Composable
private fun StoryGenerationState.statusSubtitle(elapsedSeconds: Long): String {
    if (this !is StoryGenerationState.Streaming) return ""
    if (phase == StoryGenerationPhase.Reasoning && reasoningPreview.isNotBlank()) {
        return reasoningPreview
    }
    if (
        elapsedSeconds >= SLOW_GENERATION_HINT_SECONDS &&
        phase != StoryGenerationPhase.Writing
    ) {
        return stringResource(R.string.story_generation_slow_hint)
    }
    return ""
}

@Composable
private fun EditorDialogSwitch(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit
) {
    when (val dialogState = state.dialogState) {
        StoryEditorDialogState.None -> Unit
        is StoryEditorDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { StoryEditorUiIntent.OpenProviderSettings.emit() }
        )
        is StoryEditorDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
            onCopyRequest = { StoryEditorUiIntent.CopyPromptItem(it).emit() }
        )

        StoryEditorDialogState.FileActions -> FileActionsDialog(emit)
        is StoryEditorDialogState.ImportPreview -> ImportPreviewDialog(dialogState, emit)
        is StoryEditorDialogState.StructureTitleEditor -> AppInputDialog(
            onDismissRequest = {
                if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
            },
            title = when (dialogState.target) {
                StoryStructureTitleTarget.NewVolume -> stringResource(R.string.story_add_volume)
                is StoryStructureTitleTarget.NewChapter -> stringResource(R.string.story_add_chapter)
                is StoryStructureTitleTarget.Volume,
                is StoryStructureTitleTarget.Chapter -> stringResource(R.string.rename)
            },
            value = dialogState.title,
            onValueChange = { StoryEditorUiIntent.ChangeStructureTitle(it).emit() },
            label = stringResource(R.string.story_structure_title),
            enabled = !dialogState.isSaving,
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            confirmEnabled = dialogState.title.isNotBlank() && !dialogState.isSaving,
            isConfirmLoading = dialogState.isSaving,
            onConfirm = { StoryEditorUiIntent.ConfirmStructureTitle.emit() }
        )

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
            onConfirm = { StoryEditorUiIntent.ConfirmDeleteVolume.emit() }
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
            onConfirm = { StoryEditorUiIntent.ConfirmDeleteChapter.emit() }
        )

        is StoryEditorDialogState.MoveChapter -> StoryMoveChapterDialog(
            onDismissRequest = {
                if (!dialogState.isSaving) StoryEditorUiIntent.DismissDialog.emit()
            },
            chapterTitle = dialogState.title,
            options = buildList {
                add(
                    StoryChapterDestinationOption(
                        volumeId = null,
                        title = stringResource(R.string.story_ungrouped_chapters),
                        chapterCount = state.structureState.ungroupedChapters.size
                    )
                )
                state.structureState.volumes.forEach { volume ->
                    add(
                        StoryChapterDestinationOption(
                            volumeId = volume.id,
                            title = volume.title,
                            chapterCount = volume.chapters.size
                        )
                    )
                }
            },
            selectedVolumeId = when (val destination = dialogState.selectedDestination) {
                StoryChapterDestination.Ungrouped -> null
                is StoryChapterDestination.Volume -> destination.volumeId
            },
            isSaving = dialogState.isSaving,
            onDestinationSelected = { volumeId ->
                StoryEditorUiIntent.SelectChapterDestination(
                    volumeId?.let(StoryChapterDestination::Volume)
                        ?: StoryChapterDestination.Ungrouped
                ).emit()
            },
            onConfirm = { StoryEditorUiIntent.ConfirmMoveStoryChapter.emit() }
        )

        StoryEditorDialogState.SummarizingStory -> LoadingDialog(
            title = stringResource(R.string.story_summarizing),
            description = stringResource(R.string.story_summary_desc),
            onCancel = { StoryEditorUiIntent.CancelStorySummary.emit() }
        )

        is StoryEditorDialogState.StorySummaryPreview -> StorySummaryPreviewDialog(
            dialogState,
            emit
        )
    }
}

@Composable
private fun StorySummaryPreviewDialog(
    state: StoryEditorDialogState.StorySummaryPreview,
    emit: StoryEditorUiIntent.() -> Unit
) {
    AppDialogScaffold(
        onDismissRequest = { StoryEditorUiIntent.DismissDialog.emit() },
        title = stringResource(R.string.story_summary_preview),
        badgeIcon = Icons.Rounded.Description,
        badgeTone = DialogBadgeTone.Primary,
        compactHeader = true,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { StoryEditorUiIntent.ConfirmStorySummary.emit() }
    ) {
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.content,
            onValueChange = {},
            minLines = 5,
            maxLines = 12,
            readOnly = true,
            outputTransformation = rememberPromptMacroOutputTransformation(),
            shape = RoundedCornerShape(16.dp)
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
                onClick = { StoryEditorUiIntent.ImportTextClick.emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileDownload,
                title = stringResource(R.string.story_import_archive),
                onClick = { StoryEditorUiIntent.ImportStoryClick.emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_txt),
                onClick = { StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Text).emit() }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_markdown),
                onClick = {
                    StoryEditorUiIntent.ExportTextClick(StoryTextExportFormat.Markdown).emit()
                }
            ),
            AppActionItem(
                icon = Icons.Rounded.FileUpload,
                title = stringResource(R.string.story_export_archive),
                onClick = { StoryEditorUiIntent.ExportStoryClick.emit() }
            )
        )
    )
}

@Composable
private fun ImportPreviewDialog(
    state: StoryEditorDialogState.ImportPreview,
    emit: StoryEditorUiIntent.() -> Unit
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
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = preview.title,
                onValueChange = { StoryEditorUiIntent.ChangeImportTitle(it).emit() },
                label = { Text(stringResource(R.string.story_title)) },
                singleLine = true,
                enabled = !preview.isSaving,
                shape = RoundedCornerShape(14.dp)
            )
            Text(
                text = stringResource(
                    R.string.story_import_summary,
                    preview.draft.totalCharacterCount,
                    preview.draft.chapterCount,
                    preview.draft.characterHints.size,
                    preview.draft.lorebookHints.size
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StoryOutlinePage(
    storyTitle: String,
    structureState: StoryEditorStructureState,
    emit: StoryEditorUiIntent.() -> Unit
) {
    var collapsedVolumeIds by remember { mutableStateOf(emptySet<Long>()) }
    val controlsEnabled = !structureState.isUpdating
    val totalChapterCount = structureState.ungroupedChapters.size +
            structureState.volumes.sumOf { it.chapters.size }
    val totalCharacterCount = remember(structureState) {
        structureState.ungroupedChapters.sumOf { it.characterCount } +
                structureState.volumes.sumOf { volume -> volume.chapters.sumOf { it.characterCount } }
    }

    // 通过查找表把可保存的稳定 key 转换为类型化手势目标，避免解析字符串约定。
    val listState = rememberLazyListState()
    val dragNodesByKey = remember(structureState) {
        storyOutlineDragNodes(structureState)
    }
    val dragDropState = rememberLazyListDragDropState(
        lazyListState = listState,
        isItemDraggable = { key -> controlsEnabled && dragNodesByKey[key]?.chapterId != null },
        isItemDropTarget = { key ->
            controlsEnabled && dragNodesByKey[key]?.dropTarget != null
        },
        onMove = { fromKey, toKey ->
            val chapterId = dragNodesByKey[fromKey]?.chapterId
                ?: return@rememberLazyListDragDropState
            val target = dragNodesByKey[toKey]?.dropTarget
                ?: return@rememberLazyListDragDropState
            StoryEditorUiIntent.DragStoryChapter(chapterId, target).emit()
        },
        onDragEnd = { StoryEditorUiIntent.CommitStoryChapterOrder.emit() }
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_outline),
                onBack = { StoryEditorUiIntent.CloseStoryOutline.emit() },
                actions = {
                    IconButton(
                        onClick = { StoryEditorUiIntent.ShowCreateVolumeDialog.emit() },
                        enabled = controlsEnabled
                    ) {
                        Icon(
                            Icons.Rounded.Book,
                            contentDescription = stringResource(R.string.story_add_volume)
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(null).emit() },
                        enabled = controlsEnabled
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.story_add_chapter)
                        )
                    }
                }
            )
        }
    ) { padding ->
        RpLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .dragContainer(dragDropState),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 全书体量与结构概览卡片
            item(key = OUTLINE_SUMMARY_KEY) {
                OutlineSummary(
                    storyTitle = storyTitle,
                    volumeCount = structureState.volumes.size,
                    chapterCount = totalChapterCount,
                    totalCharacterCount = totalCharacterCount,
                    updating = structureState.isUpdating
                )
            }

            // 未分卷章节分组
            val ungroupedTotalWords = structureState.ungroupedChapters.sumOf { it.characterCount }
            item(key = UNGROUPED_HEADER_KEY) {
                OutlineSectionHeader(
                    title = stringResource(R.string.story_ungrouped_chapters),
                    itemCount = structureState.ungroupedChapters.size,
                    totalCharacterCount = ungroupedTotalWords,
                    enabled = controlsEnabled,
                    onAddChapter = {
                        StoryEditorUiIntent.ShowCreateChapterDialog(null).emit()
                    }
                )
            }
            if (structureState.ungroupedChapters.isEmpty()) {
                item(key = UNGROUPED_EMPTY_KEY) {
                    OutlineEmptyMessage(
                        text = stringResource(R.string.story_no_ungrouped_chapters),
                        onAction = { StoryEditorUiIntent.ShowCreateChapterDialog(null).emit() },
                        actionTextRes = R.string.story_add_ungrouped_chapter_button,
                        enabled = controlsEnabled
                    )
                }
            } else {
                items(
                    items = structureState.ungroupedChapters,
                    key = { chapterOutlineKey(it.id) }
                ) { chapter ->
                    val index =
                        structureState.ungroupedChapters.indexOfFirst { it.id == chapter.id }
                    DraggableItem(
                        dragDropState,
                        chapterOutlineKey(chapter.id)
                    ) { isDragging ->
                        StoryChapterOutlineRow(
                            index = index,
                            chapter = chapter,
                            selected = chapter.id == structureState.currentChapterId,
                            isDragging = isDragging,
                            enabled = controlsEnabled,
                            canMoveUp = index > 0,
                            canMoveDown = index in 0 until structureState.ungroupedChapters.lastIndex,
                            canDelete = totalChapterCount > 1,
                            emit = emit
                        )
                    }
                }
                item(key = UNGROUPED_ADD_KEY) {
                    OutlineAddChapterButton(
                        textRes = R.string.story_add_ungrouped_chapter_button,
                        enabled = controlsEnabled,
                        onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(null).emit() }
                    )
                }
            }

            // 各分卷及其包含的章节列表
            structureState.volumes.forEachIndexed { volumeIndex, volume ->
                item(key = volumeHeaderKey(volume.id)) {
                    StoryVolumeOutlineHeader(
                        volume = volume,
                        collapsed = volume.id in collapsedVolumeIds,
                        enabled = controlsEnabled,
                        canMoveUp = volumeIndex > 0,
                        canMoveDown = volumeIndex < structureState.volumes.lastIndex,
                        onToggleCollapsed = {
                            collapsedVolumeIds = if (volume.id in collapsedVolumeIds) {
                                collapsedVolumeIds - volume.id
                            } else {
                                collapsedVolumeIds + volume.id
                            }
                        },
                        onAddChapter = {
                            StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit()
                        },
                        emit = emit
                    )
                }
                if (volume.id !in collapsedVolumeIds) {
                    if (volume.chapters.isEmpty()) {
                        item(key = volumeEmptyKey(volume.id)) {
                            OutlineEmptyMessage(
                                text = stringResource(R.string.story_empty_volume_guide),
                                onAction = { StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit() },
                                actionTextRes = R.string.story_add_chapter_to_volume,
                                enabled = controlsEnabled
                            )
                        }
                    } else {
                        items(
                            items = volume.chapters,
                            key = { chapterOutlineKey(it.id) }
                        ) { chapter ->
                            val index = volume.chapters.indexOfFirst { it.id == chapter.id }
                            DraggableItem(
                                dragDropState,
                                chapterOutlineKey(chapter.id)
                            ) { isDragging ->
                                StoryChapterOutlineRow(
                                    index = index,
                                    chapter = chapter,
                                    selected = chapter.id == structureState.currentChapterId,
                                    isDragging = isDragging,
                                    enabled = controlsEnabled,
                                    canMoveUp = index > 0,
                                    canMoveDown = index in 0 until volume.chapters.lastIndex,
                                    canDelete = totalChapterCount > 1,
                                    emit = emit
                                )
                            }
                        }
                        item(key = volumeAddKey(volume.id)) {
                            OutlineAddChapterButton(
                                textRes = R.string.story_add_chapter_to_volume,
                                enabled = controlsEnabled,
                                onClick = { StoryEditorUiIntent.ShowCreateChapterDialog(volume.id).emit() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineSummary(
    storyTitle: String,
    volumeCount: Int,
    chapterCount: Int,
    totalCharacterCount: Int,
    updating: Boolean
) {
    // 汇总卡片采用 Primary Container 柔和主色调与通透圆角
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标气泡
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            // 标题与聚合统计文案
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = storyTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.story_outline_stats_summary,
                        volumeCount,
                        chapterCount,
                        String.format(LocalLocale.current.platformLocale, "%,d", totalCharacterCount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (updating) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
private fun OutlineSectionHeader(
    title: String,
    itemCount: Int,
    totalCharacterCount: Int? = null,
    enabled: Boolean,
    onAddChapter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        if (totalCharacterCount != null) {
            Text(
                text = stringResource(
                    R.string.story_volume_stats,
                    itemCount,
                    String.format(LocalLocale.current.platformLocale, "%,d", totalCharacterCount)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        } else {
            Text(
                text = itemCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        IconButton(onClick = onAddChapter, enabled = enabled) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.story_add_chapter))
        }
    }
}

@Composable
private fun StoryVolumeOutlineHeader(
    volume: StoryVolumeOutlineItem,
    collapsed: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleCollapsed: () -> Unit,
    onAddChapter: () -> Unit,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val totalWords = remember(volume.chapters) {
        volume.chapters.sumOf { it.characterCount }
    }
    // 分卷头部采用容器卡片样式，包含章节数与总字数统计
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggleCollapsed)
                .padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 折叠与展开图标
            IconButton(onClick = onToggleCollapsed, enabled = enabled) {
                Icon(
                    imageVector = if (collapsed) {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // 分卷标题与统计指标
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = volume.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.story_volume_stats,
                        volume.chapters.size,
                        String.format(LocalLocale.current.platformLocale, "%,d", totalWords)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 快速为本卷添加章节按钮
            IconButton(onClick = onAddChapter, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.story_add_chapter_to_volume),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StoryVolumeMenu(
                volumeId = volume.id,
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                emit = emit
            )
        }
    }
}

@Composable
private fun StoryChapterOutlineRow(
    index: Int,
    chapter: StoryChapterOutlineItem,
    selected: Boolean,
    isDragging: Boolean = false,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 章节行卡片容器，包含序号、高亮边框、当前编辑状态徽标与拖拽手柄
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                StoryEditorUiIntent.SelectStoryChapter(chapter.id).emit()
            },
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (isDragging) 8.dp else 0.dp,
        tonalElevation = if (selected || isDragging) 3.dp else 0.dp,
        color = when {
            isDragging -> MaterialTheme.colorScheme.surfaceVariant
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (selected || isDragging) 1.5.dp else 1.dp,
            color = when {
                isDragging -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 章节两位工整序号徽标
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) {
                Text(
                    text = String.format(LocalLocale.current.platformLocale, "%02d", index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
            // 标题与字数/状态行
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (selected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = stringResource(R.string.story_active_chapter),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.story_character_count,
                            chapter.characterCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chapter.characterCount < 50) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = stringResource(R.string.story_draft_chapter),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                            )
                        }
                    }
                }
            }
            // 拖拽手柄视觉提示
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = stringResource(R.string.story_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(20.dp)
            )
            StoryChapterMenu(
                chapterId = chapter.id,
                enabled = enabled,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                canDelete = canDelete,
                emit = emit
            )
        }
    }
}

@Composable
private fun OutlineAddChapterButton(
    textRes: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun OutlineEmptyMessage(
    text: String,
    onAction: (() -> Unit)? = null,
    actionTextRes: Int? = null,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onAction != null && actionTextRes != null) {
                TextButton(onClick = onAction, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(actionTextRes), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun StoryVolumeMenu(
    volumeId: Long,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 新建章节
            OutlineMenuItem(
                textRes = R.string.story_add_chapter,
                icon = Icons.Rounded.Add,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowCreateChapterDialog(volumeId).emit()
                }
            )
            // 重命名分卷
            OutlineMenuItem(
                textRes = R.string.rename,
                icon = Icons.Rounded.Edit,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowRenameVolumeDialog(volumeId).emit()
                }
            )
            // 上移分卷
            OutlineMenuItem(
                textRes = R.string.move_up,
                icon = Icons.Rounded.ArrowUpward,
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryVolume(volumeId, -1).emit()
                }
            )
            // 下移分卷
            OutlineMenuItem(
                textRes = R.string.move_down,
                icon = Icons.Rounded.ArrowDownward,
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryVolume(volumeId, 1).emit()
                }
            )
            // 删除分卷
            OutlineMenuItem(
                textRes = R.string.delete,
                icon = Icons.Rounded.Delete,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowDeleteVolumeDialog(volumeId).emit()
                }
            )
        }
    }
}

@Composable
private fun StoryChapterMenu(
    chapterId: Long,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // 重命名章节
            OutlineMenuItem(
                textRes = R.string.rename,
                icon = Icons.Rounded.Edit,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowRenameChapterDialog(chapterId).emit()
                }
            )
            // 移动到其他分卷
            OutlineMenuItem(
                textRes = R.string.story_move_chapter,
                icon = Icons.Rounded.FolderOpen,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowMoveStoryChapterDialog(chapterId).emit()
                }
            )
            // 上移章节
            OutlineMenuItem(
                textRes = R.string.move_up,
                icon = Icons.Rounded.ArrowUpward,
                enabled = canMoveUp,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryChapter(chapterId, -1).emit()
                }
            )
            // 下移章节
            OutlineMenuItem(
                textRes = R.string.move_down,
                icon = Icons.Rounded.ArrowDownward,
                enabled = canMoveDown,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.MoveStoryChapter(chapterId, 1).emit()
                }
            )
            // 删除章节
            OutlineMenuItem(
                textRes = R.string.delete,
                icon = Icons.Rounded.Delete,
                enabled = canDelete,
                onClick = {
                    expanded = false
                    StoryEditorUiIntent.ShowDeleteChapterDialog(chapterId).emit()
                }
            )
        }
    }
}

@Composable
private fun OutlineMenuItem(
    textRes: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(textRes)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun StorySettingsPage(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() },
                actions = {
                    TextButton(
                        onClick = { StoryEditorUiIntent.SaveStorySettings.emit() },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsSectionTabs(state.selectedSection, emit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.selectedSection) {
                    StorySettingsSection.Context -> ContextSettings(state, emit)
                    StorySettingsSection.Characters -> CharacterSettings(state, emit)
                    StorySettingsSection.Lorebook -> LorebookSettings(state, emit)
                }
            }
        }
    }
}

@Composable
private fun StorySettingsLoadingPage(emit: StoryEditorUiIntent.() -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.story_loading_settings))
            }
        }
    }
}

@Composable
private fun SettingsSectionTabs(
    selected: StorySettingsSection,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab(
            selected = selected == StorySettingsSection.Context,
            label = stringResource(R.string.story_context_settings)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Context).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Characters,
            label = stringResource(R.string.story_character_references)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Characters).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Lorebook,
            label = stringResource(R.string.world_book)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Lorebook).emit() }
    }
}

@Composable
private fun SettingsTab(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun ContextSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    val controlsEnabled = !state.isSaving
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .draggableScrollIndicator(scrollState)
            .verticalScroll(scrollState)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        UserPersonaSetting(state, controlsEnabled, emit)
        SettingIntro(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = stringResource(R.string.story_memory),
            subtitle = stringResource(R.string.story_memory_desc)
        )
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.memory,
            onValueChange = { StoryEditorUiIntent.ChangeMemory(it).emit() },
            label = { Text(stringResource(R.string.story_memory)) },
            minLines = 6,
            maxLines = 12,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        SettingIntro(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.story_summary),
            subtitle = stringResource(R.string.story_summary_desc)
        )
        StorySummarySettings(state, emit)
        SettingIntro(
            icon = Icons.Rounded.Edit,
            title = stringResource(R.string.story_author_note),
            subtitle = stringResource(R.string.story_author_note_desc)
        )
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.authorNote,
            onValueChange = { StoryEditorUiIntent.ChangeAuthorNote(it).emit() },
            label = { Text(stringResource(R.string.story_author_note)) },
            minLines = 4,
            maxLines = 10,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** 同步外部文档版本时保留用户选择区，文本缩短时仅将越界位置裁剪到新文末。 */
internal fun TextFieldState.syncTextPreservingSelection(content: String) {
    val previousSelection = selection
    edit {
        replace(0, length, content)
        selection = TextRange(
            start = previousSelection.start.coerceIn(0, length),
            end = previousSelection.end.coerceIn(0, length)
        )
    }
}

@Composable
private fun UserPersonaSetting(
    state: StoryEditorPageState.Settings,
    enabled: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    StoryUserPersonaCard(
        checked = state.includeUserPersona,
        onCheckedChange = {
            StoryEditorUiIntent.SetIncludeUserPersona(it).emit()
        },
        enabled = enabled
    )
}

@Composable
private fun StorySummarySettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    RpScrollableOutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.summary,
        onValueChange = { StoryEditorUiIntent.ChangeSummary(it).emit() },
        label = { Text(stringResource(R.string.story_summary)) },
        minLines = 6,
        maxLines = 12,
        enabled = !state.isSaving,
        shape = RoundedCornerShape(16.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Button(
            onClick = { StoryEditorUiIntent.SummarizeStory.emit() },
            enabled = !state.isSaving
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.story_summarize_now))
        }
    }
}

@Composable
private fun CharacterSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    if (state.characters.isEmpty()) {
        EmptySettingsMessage(R.string.story_no_characters)
        return
    }
    val selectedCount = state.characters.count { it.selected }
    // 未选角色不属于当前故事引用，保留原有名称排序且不参与拖动
    val listState = rememberLazyListState()
    val draggableCharacterIds = remember(state.characters, state.isSaving) {
        if (state.isSaving) {
            emptySet()
        } else {
            state.characters.filter { it.selected }.map { it.id }.toSet()
        }
    }
    val dragDropState = rememberLazyListDragDropState(
        lazyListState = listState,
        isItemDraggable = { key -> key in draggableCharacterIds },
        onMove = { fromKey, toKey ->
            val selected = state.characters.filter { it.selected }
            val fromIndex = selected.indexOfFirst { it.id == fromKey }
            val toIndex = selected.indexOfFirst { it.id == toKey }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                StoryEditorUiIntent.MoveStoryCharacter(
                    characterId = selected[fromIndex].id,
                    offset = toIndex - fromIndex
                ).emit()
            }
        }
    )
    RpLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragContainer(dragDropState),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_character_references_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.characters, key = { it.id }) { character ->
            val selectedIndex = state.characters
                .filter { it.selected }
                .indexOfFirst { it.id == character.id }
            DraggableItem(dragDropState, character.id) {
                CharacterSettingCard(
                    character = character,
                    canMoveUp = character.selected && selectedIndex > 0,
                    canMoveDown = character.selected && selectedIndex in 0 until selectedCount - 1,
                    emit = emit
                )
            }
        }
    }
}

private const val OUTLINE_SUMMARY_KEY = "outline-summary"
private const val UNGROUPED_HEADER_KEY = "ungrouped-header"
private const val UNGROUPED_EMPTY_KEY = "ungrouped-empty"
private const val UNGROUPED_ADD_KEY = "ungrouped-add-button"

/** 大纲列表节点对应的拖动源和类型化投放目标。 */
private data class StoryOutlineDragNode(
    val chapterId: Long? = null,
    val dropTarget: StoryChapterDropTarget? = null
)

/** 为当前大纲建立稳定列表 key 到拖放语义的查找表。 */
private fun storyOutlineDragNodes(
    state: StoryEditorStructureState
): Map<Any, StoryOutlineDragNode> = buildMap {
    // 分组头部和空状态接收首部投放，追加按钮接收尾部投放。
    put(
        UNGROUPED_HEADER_KEY,
        StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(null, StoryChapterDropPosition.Start)
        )
    )
    put(UNGROUPED_EMPTY_KEY, getValue(UNGROUPED_HEADER_KEY))
    put(
        UNGROUPED_ADD_KEY,
        StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(null, StoryChapterDropPosition.End)
        )
    )
    state.volumes.forEach { volume ->
        val startNode = StoryOutlineDragNode(
            dropTarget = StoryChapterDropTarget.Container(
                volume.id,
                StoryChapterDropPosition.Start
            )
        )
        put(volumeHeaderKey(volume.id), startNode)
        put(volumeEmptyKey(volume.id), startNode)
        put(
            volumeAddKey(volume.id),
            StoryOutlineDragNode(
                dropTarget = StoryChapterDropTarget.Container(
                    volume.id,
                    StoryChapterDropPosition.End
                )
            )
        )
    }
    // 章节节点既是拖动源，也是插入到该章节之前的投放锚点。
    (state.ungroupedChapters + state.volumes.flatMap { it.chapters }).forEach { chapter ->
        put(
            chapterOutlineKey(chapter.id),
            StoryOutlineDragNode(
                chapterId = chapter.id,
                dropTarget = StoryChapterDropTarget.Chapter(chapter.id)
            )
        )
    }
}

private fun chapterOutlineKey(chapterId: Long): String = "chapter-$chapterId"

private fun volumeHeaderKey(volumeId: Long): String = "volume-$volumeId"

private fun volumeEmptyKey(volumeId: Long): String = "volume-$volumeId-empty"

private fun volumeAddKey(volumeId: Long): String = "volume-$volumeId-add-button"

@Composable
private fun CharacterSettingCard(
    character: StoryCharacterOptionItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (character.selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (character.selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Group)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = character.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = character.selected,
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleStoryCharacter(character.id).emit()
                    }
                )
            }
            if (character.selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Primary,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Primary
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_primary)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Always,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Always
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_always)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Auto,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Auto
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_auto)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, -1).emit()
                        },
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up)
                        )
                    }
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, 1).emit()
                        },
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down)
                        )
                    }
                }
                character.linkedLorebookName?.let { lorebookName ->
                    RpTagRow(
                        tags = listOf(
                            stringResource(R.string.story_linked_lorebook, lorebookName)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    if (state.lorebookGroups.isEmpty()) {
        EmptySettingsMessage(R.string.no_world_books)
        return
    }
    RpLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_lorebook_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.lorebookGroups, key = { it.id }) { group ->
            LorebookGroupCard(
                group = group,
                emit = emit
            )
        }
    }
}

@Composable
private fun LorebookGroupCard(
    group: StoryLorebookGroupItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    var expanded by remember(group.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (group.selectedCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        color = if (group.selectedCount > 0) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                RpIconBubble(Icons.Rounded.Book)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            group.selectedCount,
                            group.entries.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = group.isAllSelected,
                    enabled = group.entries.isNotEmpty(),
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleLorebook(group.id).emit()
                    }
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (group.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.story_lorebook_empty),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        group.entries.forEach { entry ->
                            LorebookEntryRow(entry, emit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryRow(
    entry: StoryLorebookEntryItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit() },
        shape = RoundedCornerShape(14.dp),
        color = if (entry.selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Book)
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val constantLabel = if (entry.constant) {
                        stringResource(R.string.entry_constant)
                    } else {
                        null
                    }
                    val tags = listOfNotNull(constantLabel) + entry.keywords
                    if (tags.isNotEmpty()) {
                        RpTagRow(tags = tags, maxCount = 3)
                    } else {
                        Text(
                            text = entry.contentPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = entry.selected,
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit()
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingIntro(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(icon)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySettingsMessage(messageRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(messageRes),
            modifier = Modifier.padding(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
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
                topBarState = StoryEditorTopBarState(
                    title = "Rain over the old city"
                ),
                contentState = StoryEditorContentState(
                    characterCount = 128
                ),
                structureState = StoryEditorStructureState(
                    currentChapterId = 1L,
                    currentChapterTitle = "Chapter One",
                    ungroupedChapters = listOf(
                        StoryChapterOutlineItem(1L, "Chapter One", null, 128, 0)
                    )
                ),
                referenceState = StoryEditorReferenceState(
                    hasMemory = true,
                    hasAuthorNote = true,
                    characterCount = 2,
                    lorebookEntryCount = 4
                )
            ),
            document = StoryEditorDocument(
                storyId = 1L,
                chapterId = 1L,
                content = "# Chapter One\n\nRain tapped softly against the station windows.",
                syncVersion = 1L
            ),
            emit = {}
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

@Preview(widthDp = 390, showBackground = true)
@Composable
private fun StoryGenerationStatusCardPreview() {
    AppTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            StoryGenerationStatusCard(
                generationState = StoryGenerationState.Streaming(
                    partialText = "",
                    phase = StoryGenerationPhase.Reasoning,
                    startedAtElapsedRealtime = SystemClock.elapsedRealtime() - 8_000L,
                    reasoningPreview = "正在协调人物动机，并选择适合承接上一段的场景冲突。",
                    reasoningDetail = "先延续雨夜车站的氛围，再通过人物迟疑表现未说出口的矛盾。",
                    isReasoningExpanded = true
                ),
                emit = {}
            )
        }
    }
}

private const val SLOW_GENERATION_HINT_SECONDS = 12L
private const val REASONING_SCROLL_BOTTOM_TOLERANCE_PX = 2
