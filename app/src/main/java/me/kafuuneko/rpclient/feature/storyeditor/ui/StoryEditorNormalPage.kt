package me.kafuuneko.rpclient.feature.storyeditor.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryContinuationInputState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorContentState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorReferenceState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorStructureState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationFailure
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationPhase
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySaveState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.LocalPageBottomBarOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageContentOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.pageSurfaceBlur
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 渲染故事编辑主页，编辑状态仍在自适应布局分支之外共享。
 *
 * @param state 当前故事页面状态
 * @param document 当前章节文档
 * @param emit 原有故事编辑意图发送入口
 */
@Composable
internal fun StoryEditorNormalPage(
    state: StoryEditorUiState.Normal,
    document: StoryEditorDocument?,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 编辑状态位于自适应布局分支之外，窗口尺寸切换时继续复用当前草稿与光标。
    val editorState = remember(document?.chapterId) {
        TextFieldState(document?.content.orEmpty())
    }
    val documentMatchesChapter = document?.chapterId == state.structureState.currentChapterId
    val onContinue = document?.takeIf { documentMatchesChapter }?.let { currentDocument ->
        {
            StoryEditorUiIntent.ContinueStory(
                snapshot = StoryEditorSnapshot(
                    chapterId = currentDocument.chapterId,
                    content = editorState.text.toString(),
                    isComposing = editorState.composition != null,
                ),
            ).emit()
        }
    }

    // 在自适应布局分支上方共享预览状态，确保各工具栏控制同一编辑器。
    val markdownController =
        rememberStoryMarkdownController(document, state.contentState.markdownRenderingEnabled)
    // 窗口等级不随 IME 开合变化，避免输入时切换布局并打断焦点。
    val layoutMode = storyEditorLayoutMode(currentWindowAdaptiveInfo().windowSizeClass)
    CompositionLocalProvider(LocalStoryMarkdownEditorController provides markdownController) {
        StoryEditorScaffold(
            state = state,
            document = document,
            editorState = editorState,
            documentMatchesChapter = documentMatchesChapter,
            layoutMode = layoutMode,
            onContinue = onContinue,
            emit = emit,
        )
    }
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
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
                    emit = emit,
                )
            }
        },
        content = { paddingValues ->
            when (layoutMode) {
                StoryEditorLayoutMode.Regular -> StoryEditorRegularContent(
                    state = state,
                    document = document,
                    editorState = editorState,
                    documentMatchesChapter = documentMatchesChapter,
                    paddingValues = paddingValues,
                    emit = emit,
                )

                StoryEditorLayoutMode.CompactWide -> StoryEditorLandscapeContent(
                    state = state,
                    document = document,
                    editorState = editorState,
                    documentMatchesChapter = documentMatchesChapter,
                    paddingValues = paddingValues,
                    onContinue = onContinue,
                    emit = emit,
                )

                StoryEditorLayoutMode.CompactNarrow -> StoryEditorCompactContent(
                    state = state,
                    document = document,
                    editorState = editorState,
                    documentMatchesChapter = documentMatchesChapter,
                    paddingValues = paddingValues,
                    onContinue = onContinue,
                    showStatistics = false,
                    emit = emit,
                )
            }
        },
    )
}

/** 渲染故事编辑器顶栏及页面级操作。 */
@Composable
private fun StoryEditorTopBar(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    AppTopBar(
        title = state.topBarState.title,
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = LocalPageTopBarOpacity.current),
        modifier = Modifier.pageSurfaceBlur(PageSurfaceKind.TopBar),
        onBack = { StoryEditorUiIntent.Back.emit() },
        actions = {
            // 保存状态常驻顶栏，窗口紧凑时仍能看到草稿是否安全落库。
            SaveStatus(state.topBarState.saveState, emit)
            IconButton(
                onClick = { StoryEditorUiIntent.OpenPromptInspector.emit() },
                enabled = state.hasPromptInspection,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.prompt_inspector_title),
                )
            }
            IconButton(
                onClick = { StoryEditorUiIntent.OpenFileActions.emit() },
                enabled = state.generationState is StoryGenerationState.Idle,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = stringResource(R.string.story_file_actions),
                )
            }
            IconButton(
                onClick = { StoryEditorUiIntent.OpenStorySettings.emit() },
                enabled = state.generationState is StoryGenerationState.Idle,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.story_settings),
                )
            }
        },
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
            emit = emit,
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
    emit: StoryEditorUiIntent.() -> Unit,
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
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧极简导航轨
        StoryEditorLeftRail(
            state = state,
            document = document,
            editorState = editorState,
            documentMatchesChapter = documentMatchesChapter,
            compactMode = imeVisible,
            emit = emit,
        )

        // 中央沉浸稿纸区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
                emit = emit,
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
            emit = emit,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    PageSurface(
        kind = PageSurfaceKind.TopBar,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f * LocalPageTopBarOpacity.current),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .width(if (compactMode) 42.dp else 48.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶部：返回按钮与极简保存状态
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { StoryEditorUiIntent.Back.emit() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        modifier = Modifier.size(20.dp),
                    )
                }
                SaveStatusMini(saveState = state.topBarState.saveState, emit = emit)
            }

            // 中部：分卷与大纲快速入口
            IconButton(
                onClick = {
                    document?.takeIf { documentMatchesChapter }?.let { currentDoc ->
                        StoryEditorUiIntent.OpenStoryOutline(
                            snapshot = StoryEditorSnapshot(
                                chapterId = currentDoc.chapterId,
                                content = editorState.text.toString(),
                                isComposing = editorState.composition != null,
                            ),
                        ).emit()
                    }
                },
                enabled = documentMatchesChapter
                    && state.contentState.editable
                    && state.generationState is StoryGenerationState.Idle,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                    contentDescription = stringResource(R.string.story_outline),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // 底部：检查器、导入导出、设置
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!compactMode) {
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenPromptInspector.emit() },
                        enabled = state.hasPromptInspection,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = stringResource(R.string.prompt_inspector_title),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    IconButton(
                        onClick = { StoryEditorUiIntent.OpenFileActions.emit() },
                        enabled = state.generationState is StoryGenerationState.Idle,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = stringResource(R.string.story_file_actions),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { StoryEditorUiIntent.OpenStorySettings.emit() },
                    enabled = state.generationState is StoryGenerationState.Idle,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.story_settings),
                        modifier = Modifier.size(19.dp),
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 紧凑状态指示沿用保存状态映射，不参与文档保存流程。
    val clickable = saveState == StorySaveState.Failed
    val saveStatusColor = when (saveState) {
        StorySaveState.Failed,
        StorySaveState.Conflict -> MaterialTheme.colorScheme.errorContainer

        StorySaveState.Dirty,
        StorySaveState.Saving -> MaterialTheme.colorScheme.secondaryContainer

        StorySaveState.Saved -> MaterialTheme.colorScheme.surfaceVariant
    }
    AppSurface(
        modifier = Modifier.clickable(
            enabled = clickable,
            onClick = {
                StoryEditorUiIntent.RetrySave.emit()
            },
        ),
        shape = CircleShape,
        color = saveStatusColor,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            val saveStatusIcon = when (saveState) {
                StorySaveState.Saved -> Icons.Rounded.Check
                StorySaveState.Dirty -> Icons.Rounded.Save
                StorySaveState.Saving -> Icons.Rounded.HourglassTop
                StorySaveState.Failed,
                StorySaveState.Conflict -> Icons.Rounded.ErrorOutline
            }
            val saveStatusIconTint = when (saveState) {
                StorySaveState.Failed,
                StorySaveState.Conflict -> MaterialTheme.colorScheme.onErrorContainer

                StorySaveState.Dirty,
                StorySaveState.Saving -> MaterialTheme.colorScheme.onSecondaryContainer

                StorySaveState.Saved -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = saveStatusIcon,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = saveStatusIconTint,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val historyEnabled = contentState.editable && generationState is StoryGenerationState.Idle

    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f * LocalPageBottomBarOpacity.current),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .width(if (compactMode) 44.dp else 52.dp)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 顶部：字数微型指示
            if (!compactMode) {
                AppSurface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f * LocalPageBottomBarOpacity.current),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = if (characterCount >= 1000) {
                                String.format(
                                    LocalLocale.current.platformLocale,
                                    "%.1fk",
                                    characterCount / 1000f
                                )
                            } else {
                                "$characterCount"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 中部：灵感引导折叠切换与撤销/重做
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 灵感引导展开按钮
                AppSurface(
                    modifier = Modifier.clickable(
                        enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleGuidance()
                        },
                    ),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isGuidanceExpanded || continuationInputState.guidanceDraft.isNotBlank()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    },
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.story_continuation_guidance),
                            modifier = Modifier.size(16.dp),
                            tint = if (isGuidanceExpanded || continuationInputState.guidanceDraft.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        StoryEditorUiIntent.UndoLastEdit.emit()
                    },
                    enabled = historyEnabled && canUndoEdit,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = stringResource(R.string.story_undo),
                        modifier = Modifier.size(17.dp),
                    )
                }
                IconButton(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        StoryEditorUiIntent.RedoLastEdit.emit()
                    },
                    enabled = historyEnabled && canRedoEdit,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = stringResource(R.string.story_redo),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }

            // 底部：AI 续写大按键
            LandscapeGenerationAction(
                contentState = contentState,
                generationState = generationState,
                onContinue = onContinue,
                hapticFeedback = hapticFeedback,
                emit = emit,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 横屏生成操作沿用原有可用条件和按钮回调。
    when (generationState) {
        is StoryGenerationState.Streaming -> Button(
            modifier = Modifier.size(42.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                StoryEditorUiIntent.StopGeneration.emit()
            },
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.story_stop_generation),
                modifier = Modifier.size(20.dp),
            )
        }

        StoryGenerationState.Preparing,
        StoryGenerationState.Applying -> AppSurface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        else -> Button(
            modifier = Modifier.size(42.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onContinue?.invoke()
            },
            enabled = contentState.editable
                && generationState is StoryGenerationState.Idle
                && onContinue != null,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.story_continue),
                modifier = Modifier.size(20.dp),
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 内联极简章节条
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            // 当前章节名称微胶囊
            AppSurface(
                modifier = Modifier.clickable(
                    enabled = documentMatchesChapter
                        && state.contentState.editable
                        && state.generationState is StoryGenerationState.Idle,
                    onClick = {
                        document?.takeIf { documentMatchesChapter }?.let { currentDoc ->
                            StoryEditorUiIntent.OpenStoryOutline(
                                snapshot = StoryEditorSnapshot(
                                    chapterId = currentDoc.chapterId,
                                    content = editorState.text.toString(),
                                    isComposing = editorState.composition != null,
                                ),
                            ).emit()
                        }
                    },
                ),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f * LocalPageContentOpacity.current),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = state.structureState.currentChapterTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.story_open_outline),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // 展开的引导输入框（如果展开）
        AnimatedVisibility(
            visible = isGuidanceExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .generalContentBackground(RoundedCornerShape(12.dp)),
                value = state.continuationInputState.guidanceDraft,
                onValueChange = { text ->
                    StoryEditorUiIntent.ChangeContinuationGuidance(text).emit()
                },
                enabled = state.contentState.editable && state.generationState is StoryGenerationState.Idle,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.story_continuation_guidance_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    if (state.continuationInputState.guidanceDraft.isNotBlank()) {
                        IconButton(
                            onClick = { StoryEditorUiIntent.ChangeContinuationGuidance("").emit() },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.prompt_editor_clear),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
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
                emit = emit,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val imeVisible = WindowInsets.isImeVisible
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .consumeWindowInsets(paddingValues)
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            emit = emit,
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
            emit = emit,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compactChapterBar) 6.dp else 10.dp),
    ) {
        // 键盘占用紧凑窗口时暂时收起章节入口，为正文保留稳定的可编辑高度。
        if (showChapterBar) {
            CurrentChapterBar(
                state = state.structureState,
                enabled = documentMatchesChapter
                    && state.contentState.editable
                    && state.generationState is StoryGenerationState.Idle,
                compact = compactChapterBar,
                onClick = document?.takeIf { documentMatchesChapter }?.let { currentDocument ->
                    {
                        StoryEditorUiIntent.OpenStoryOutline(
                            snapshot = StoryEditorSnapshot(
                                chapterId = currentDocument.chapterId,
                                content = editorState.text.toString(),
                                isComposing = editorState.composition != null,
                            ),
                        ).emit()
                    }
                },
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
                emit = emit,
            )
        }
    }
}

/** 汇总保存、生成和模型配置问题提示。 */
@Composable
private fun StoryEditorProblemBanners(
    state: StoryEditorUiState.Normal,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    SaveProblemBanner(state.topBarState.saveState, emit)
    GenerationProblemBanner(state.generationState, emit)
    if (!state.hasAvailableProvider) {
        NoProviderBanner(onClick = { StoryEditorUiIntent.OpenProviderSettings.emit() })
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
    onClick: (() -> Unit)?,
) {
    // 章节卡片与正文共用内容区设置，不额外绘制装饰边框。
    PageSurface(
        kind = PageSurfaceKind.Content,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && onClick != null, onClick = { onClick?.invoke() }),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f * LocalPageContentOpacity.current),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 6.dp else 10.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 章节主题图标指示
            Icon(
                imageVector = Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 20.dp else 22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            // 紧凑高度只保留章节标题，分卷信息可在大纲页继续查看。
            if (compact) {
                Text(
                    text = state.currentChapterTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.currentChapterTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.currentVolumeTitle
                            ?: stringResource(R.string.story_ungrouped_chapters),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // 进入大纲的轻量胶囊按钮提示
            AppSurface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f * LocalPageContentOpacity.current),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!compact) {
                        Text(
                            text = stringResource(R.string.story_outline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveStatus(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 保存状态只负责展示，失败后的操作继续通过原有意图处理。
    val clickable = saveState == StorySaveState.Failed
    val saveStatusColor = when (saveState) {
        StorySaveState.Failed,
        StorySaveState.Conflict -> MaterialTheme.colorScheme.errorContainer

        StorySaveState.Dirty,
        StorySaveState.Saving -> MaterialTheme.colorScheme.secondaryContainer

        StorySaveState.Saved -> MaterialTheme.colorScheme.surfaceVariant
    }
    AppSurface(
        modifier = Modifier.clickable(
            enabled = clickable,
            onClick = {
                StoryEditorUiIntent.RetrySave.emit()
            },
        ),
        shape = RoundedCornerShape(percent = 50),
        color = saveStatusColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val saveStatusIcon = when (saveState) {
                StorySaveState.Saved -> Icons.Rounded.Check
                StorySaveState.Dirty -> Icons.Rounded.Save
                StorySaveState.Saving -> Icons.Rounded.HourglassTop
                StorySaveState.Failed,
                StorySaveState.Conflict -> Icons.Rounded.ErrorOutline
            }
            Icon(
                imageVector = saveStatusIcon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            val saveStatusTextResId = when (saveState) {
                StorySaveState.Saved -> R.string.story_saved
                StorySaveState.Dirty -> R.string.story_unsaved
                StorySaveState.Saving -> R.string.story_saving
                StorySaveState.Failed -> R.string.story_save_failed_short
                StorySaveState.Conflict -> R.string.story_save_conflict_short
            }
            Text(
                text = stringResource(id = saveStatusTextResId),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SaveProblemBanner(
    saveState: StorySaveState,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 失败状态决定提示和恢复操作，不在视图内修改文档。
    when (saveState) {
        StorySaveState.Failed -> AppCard(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.story_save_failed_message),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { StoryEditorUiIntent.RetrySave.emit() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }

        StorySaveState.Conflict -> AppCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.story_save_conflict_message),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { StoryEditorUiIntent.CopyConflictDraft.emit() }) {
                        Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_copy_draft))
                    }
                    TextButton(onClick = { StoryEditorUiIntent.ReloadAfterConflict.emit() }) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 生成失败与恢复操作继续使用原有状态分支和意图。
    val failure = generationState as? StoryGenerationState.Failed ?: return
    AppCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(
                alpha = 0.8f
            )
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val failureMessageResId = when (failure.reason) {
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
            Text(
                text = stringResource(id = failureMessageResId),
                style = MaterialTheme.typography.bodySmall,
            )
            if (failure.detail.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.story_generation_largest_characters,
                        failure.detail,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
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
                        text = stringResource(
                            id = if (failure.recoverablePartial.isBlank()) {
                                R.string.close
                            } else {
                                R.string.story_discard
                            },
                        ),
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val historyEnabled = contentState.editable && generationState is StoryGenerationState.Idle
    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp * LocalPageBottomBarOpacity.current,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 引导输入压缩为单行，避免紧凑窗口继续堆叠两层底部控件。
            OutlinedTextField(
                modifier = Modifier
                    .weight(1f)
                    .generalContentBackground(RoundedCornerShape(14.dp)),
                value = continuationInputState.guidanceDraft,
                onValueChange = { text ->
                    StoryEditorUiIntent.ChangeContinuationGuidance(text).emit()
                },
                enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.story_continuation_guidance),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = RoundedCornerShape(14.dp),
            )
            if (showStatistics) {
                CompactEditorStatistics(
                    characterCount = characterCount,
                    referenceState = referenceState,
                )
            }
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StoryEditorUiIntent.UndoLastEdit.emit()
                },
                enabled = historyEnabled && canUndoEdit,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.story_undo),
                )
            }
            IconButton(
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    StoryEditorUiIntent.RedoLastEdit.emit()
                },
                enabled = historyEnabled && canRedoEdit,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.story_redo),
                )
            }
            // 生成中的紧凑按钮与常规底栏保持相同的停止和禁用语义。
            CompactGenerationAction(
                contentState = contentState,
                generationState = generationState,
                onContinue = onContinue,
                hapticFeedback = hapticFeedback,
                emit = emit,
            )
        }
    }
}

/** 在宽屏紧凑工具栏中展示正文与引用规模。 */
@Composable
private fun CompactEditorStatistics(
    characterCount: Int,
    referenceState: StoryEditorReferenceState,
) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // 统计保持双行紧凑结构，不与续写输入争夺主要宽度。
        Text(
            text = stringResource(R.string.story_character_count, characterCount),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = stringResource(
                        R.string.story_character_references_count,
                        referenceState.characterCount,
                    ),
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${referenceState.characterCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Book,
                    contentDescription = stringResource(
                        R.string.story_lorebook_entries_count,
                        referenceState.lorebookEntryCount,
                    ),
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${referenceState.lorebookEntryCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 状态分支只改变尾部动作，紧凑工具栏的输入与历史操作始终保持稳定位置。
    when (generationState) {
        is StoryGenerationState.Streaming -> Button(
            modifier = Modifier.size(48.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                StoryEditorUiIntent.StopGeneration.emit()
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.story_stop_generation),
            )
        }

        StoryGenerationState.Preparing,
        StoryGenerationState.Applying -> Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }

        else -> Button(
            modifier = Modifier.size(48.dp),
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onContinue?.invoke()
            },
            enabled = contentState.editable
                && generationState is StoryGenerationState.Idle
                && onContinue != null,
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = stringResource(R.string.story_continue),
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        modifier = modifier,
        tonalElevation = 3.dp * LocalPageBottomBarOpacity.current,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    other = if (applyBottomInsets) {
                        // 导航栏与 IME 分别声明，避免支持窗格把键盘高度计入自身尺寸。
                        Modifier
                            .navigationBarsPadding()
                            .imePadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            RpScrollableOutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp),
                value = continuationInputState.guidanceDraft,
                onValueChange = { text ->
                    StoryEditorUiIntent.ChangeContinuationGuidance(text).emit()
                },
                enabled = contentState.editable && generationState is StoryGenerationState.Idle,
                minLines = 1,
                maxLines = 4,
                label = { Text(stringResource(R.string.story_continuation_guidance)) },
                placeholder = { Text(stringResource(R.string.story_continuation_guidance_placeholder)) },
                shape = RoundedCornerShape(16.dp),
            )
            when (generationState) {
                StoryGenerationState.Preparing,
                is StoryGenerationState.Streaming,
                StoryGenerationState.Applying -> StoryGenerationStatusCard(
                    generationState = generationState,
                    emit = emit,
                )

                else -> Unit
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = stringResource(R.string.story_character_count, characterCount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = stringResource(
                                    R.string.story_character_references_count,
                                    referenceState.characterCount,
                                ),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${referenceState.characterCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Book,
                                contentDescription = stringResource(
                                    R.string.story_lorebook_entries_count,
                                    referenceState.lorebookEntryCount,
                                ),
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${referenceState.lorebookEntryCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(imageVector = Icons.Rounded.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.story_stop_generation))
                    }

                    StoryGenerationState.Preparing,
                    StoryGenerationState.Applying -> Unit

                    else -> {
                        val historyEnabled = contentState.editable
                            && generationState is StoryGenerationState.Idle
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.UndoLastEdit.emit()
                            },
                            enabled = historyEnabled && canUndoEdit,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = stringResource(R.string.story_undo),
                            )
                        }
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                StoryEditorUiIntent.RedoLastEdit.emit()
                            },
                            enabled = historyEnabled && canRedoEdit,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Redo,
                                contentDescription = stringResource(R.string.story_redo),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Button(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onContinue?.invoke()
                            },
                            enabled = contentState.editable
                                && generationState is StoryGenerationState.Idle
                                && onContinue != null,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null)
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
    emit: StoryEditorUiIntent.() -> Unit,
) {
    val streaming = generationState as? StoryGenerationState.Streaming
    val elapsedSeconds =
        streaming?.let { rememberGenerationElapsedSeconds(it.startedAtElapsedRealtime) }
    val hasReasoning = streaming?.reasoningPreview?.isNotBlank() == true
    val isExpanded = streaming?.isReasoningExpanded == true
    val hapticFeedback = LocalHapticFeedback.current
    val statusTitle = generationState.statusTitle()
    val statusSubtitle = generationState.statusSubtitle(elapsedSeconds ?: 0L)
    // 仅在确实收到推理内容后允许展开，避免无反馈的伪交互
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = 12.dp, top = 8.dp)
        .clickable(
            enabled = hasReasoning,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                StoryEditorUiIntent.ToggleGenerationReasoning.emit()
            },
        )
    AppSurface(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f * LocalPageBottomBarOpacity.current),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 标题区保持紧凑，在小屏上优先压缩状态文本而不是操作图标
            StoryGenerationStatusHeader(
                title = statusTitle,
                subtitle = statusSubtitle,
                elapsedSeconds = elapsedSeconds,
                hasReasoning = hasReasoning,
                isExpanded = isExpanded,
            )
            // 推理详情限制最大高度，避免长构思挤占整个编辑器
            StoryGenerationReasoningDetail(
                reasoningDetail = streaming?.reasoningDetail.orEmpty(),
                visible = isExpanded,
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
    isExpanded: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 图标与文字沿用故事页的圆角、弱强调信息密度
        StoryGenerationStatusIcon()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.72f),
                    maxLines = if (isExpanded) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // 耗时和展开箭头固定在尾部，状态标题在窄屏下优先让出空间
        elapsedSeconds?.let { id -> StoryGenerationElapsedBadge(id) }
        if (hasReasoning) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = stringResource(if (isExpanded) R.string.hide else R.string.show),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 展开显示受限高度的模型构思详情。 */
@Composable
private fun StoryGenerationReasoningDetail(
    reasoningDetail: String,
    visible: Boolean,
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
    LaunchedEffect(key1 = scrollState, key2 = visible, key3 = shouldFollowBottom) {
        if (!visible || !shouldFollowBottom) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }
            .distinctUntilChanged()
            .collectLatest { bottom -> scrollState.animateScrollTo(bottom) }
    }
    AnimatedVisibility(
        visible = visible && reasoningDetail.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        // 内层 Surface 将模型文本与可操作的外层状态卡作视觉分组
        AppSurface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f * LocalPageBottomBarOpacity.current),
            border = BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            ),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        )
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 用弱强调胶囊展示当前生成任务已经持续的秒数。 */
@Composable
private fun StoryGenerationElapsedBadge(elapsedSeconds: Long) {
    AppSurface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f * LocalPageBottomBarOpacity.current),
    ) {
        Text(
            text = stringResource(R.string.story_generation_elapsed_seconds, elapsedSeconds),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            delay(timeMillis = 1_000L)
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
        elapsedSeconds >= SLOW_GENERATION_HINT_SECONDS
        && phase != StoryGenerationPhase.Writing
    ) {
        return stringResource(R.string.story_generation_slow_hint)
    }
    return ""
}

/** 同步外部文档版本时保留用户选择区，文本缩短时仅将越界位置裁剪到新文末。 */
internal fun TextFieldState.syncTextPreservingSelection(content: String) {
    val previousSelection = selection
    edit {
        replace(start = 0, end = length, text = content)
        selection = TextRange(
            start = previousSelection.start.coerceIn(minimumValue = 0, maximumValue = length),
            end = previousSelection.end.coerceIn(minimumValue = 0, maximumValue = length),
        )
    }
}

@Preview(widthDp = 390, showBackground = true)
@Composable
private fun StoryGenerationStatusCardPreview() {
    AppTheme(dynamicColor = false) {
        AppSurface(color = MaterialTheme.colorScheme.background) {
            StoryGenerationStatusCard(
                generationState = StoryGenerationState.Streaming(
                    partialText = "",
                    phase = StoryGenerationPhase.Reasoning,
                    startedAtElapsedRealtime = SystemClock.elapsedRealtime() - 8_000L,
                    reasoningPreview = "正在协调人物动机，并选择适合承接上一段的场景冲突。",
                    reasoningDetail = "先延续雨夜车站的氛围，再通过人物迟疑表现未说出口的矛盾。",
                    isReasoningExpanded = true,
                ),
                emit = {},
            )
        }
    }
}

private const val SLOW_GENERATION_HINT_SECONDS = 12L

private const val REASONING_SCROLL_BOTTOM_TOLERANCE_PX = 2
