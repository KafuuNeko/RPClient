package me.kafuuneko.rpclient.feature.storyeditor.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditedTextRange
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorDocument
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryEditorSnapshot
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryGenerationState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageContentOpacity
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.utils.MarkdownHighlightMapper
import kotlin.time.Duration.Companion.milliseconds

/** 编辑器与工具栏共享的 Markdown 模式和编辑确认操作。 */
internal class StoryMarkdownEditorController(val enabled: Boolean, initialPreview: Boolean = true) {
    var preview by mutableStateOf(enabled && initialPreview)
    var onConfirm: (() -> Unit)? = null
}

internal val LocalStoryMarkdownEditorController = compositionLocalOf {
    StoryMarkdownEditorController(enabled = false)
}

/** 搜索定位进入原文；编辑清除搜索范围后保持当前模式，不打断正在进行的输入。 */
@Composable
internal fun rememberStoryMarkdownController(document: StoryEditorDocument?, enabled: Boolean): StoryMarkdownEditorController {
    val controller = remember(document?.chapterId, enabled) {
        StoryMarkdownEditorController(enabled = enabled, initialPreview = document?.searchRange == null)
    }
    LaunchedEffect(document?.chapterId, document?.searchRange) {
        if (document?.searchRange != null) controller.preview = false
    }
    return controller
}

/** 在预览与原文编辑之间切换，确认时由正文组件提交最后的编辑快照。 */
@Composable
internal fun MarkdownModeToolbarAction() {
    val controller = LocalStoryMarkdownEditorController.current
    if (controller.enabled) {
        TextButton(
            onClick = {
                if (controller.preview) {
                    controller.preview = false
                } else {
                    controller.onConfirm?.invoke()
                }
            },
        ) {
            Text(stringResource(if (controller.preview) R.string.story_markdown_edit else R.string.confirm))
        }
    }
}

/** 正文编辑与 Markdown 预览共用文档快照，保留搜索和生成结果的定位行为。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StoryTextEditor(
    modifier: Modifier,
    document: StoryEditorDocument,
    editorState: TextFieldState,
    editable: Boolean,
    generationState: StoryGenerationState,
    emit: StoryEditorUiIntent.() -> Unit,
) {
    // 正文不使用 rememberSaveable，避免长文进入 Activity Bundle；Room 承担进程恢复。
    val editorScrollState = rememberScrollState()
    val searchScope = rememberCoroutineScope()
    var pendingSearchScroll by remember(document.chapterId, document.searchRange) { mutableStateOf(document.searchRange != null) }
    val markdownPreviewScrollState = rememberScrollState()
    var editorTextLayout by remember(document.chapterId) { mutableStateOf<TextLayoutResult?>(value = null) }
    var editorTextLayoutSyncVersion by remember(document.chapterId) { mutableLongStateOf(-1L) }
    var editorTextLayoutLength by remember(document.chapterId) { mutableIntStateOf(-1) }
    var markdownHighlightedStartY by remember(document.chapterId) { mutableStateOf<Float?>(value = null) }
    var markdownHighlightedPositionSyncVersion by remember(document.chapterId) { mutableLongStateOf(-1L) }
    var markdownHighlightedPositionLength by remember(document.chapterId) { mutableIntStateOf(-1) }
    var shouldScrollToGeneratedRange by remember(document.chapterId) { mutableStateOf(false) }
    val markdownEnabled = LocalStoryMarkdownEditorController.current.enabled
    val markdownController = LocalStoryMarkdownEditorController.current
    val markdownPreview = markdownController.preview
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val returnToMarkdownPreview: () -> Unit = {
        // 离开原文编辑前发送最新内容快照，避免预览丢失尚未发布的修改。
        StoryEditorUiIntent.EditorSnapshotChanged(
            snapshot = StoryEditorSnapshot(
                chapterId = document.chapterId,
                content = editorState.text.toString(),
                isComposing = editorState.composition != null,
            ),
        ).emit()
        markdownController.preview = true
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    SideEffect {
        markdownController.onConfirm = returnToMarkdownPreview
    }
    BackHandler(
        enabled = markdownEnabled && !markdownPreview,
        onBack = {
            returnToMarkdownPreview()
        },
    )
    SynchronizeMarkdownViewport(
        markdownPreview = markdownPreview,
        markdownEnabled = markdownEnabled,
        editorScrollState = editorScrollState,
        markdownPreviewScrollState = markdownPreviewScrollState,
    )
    val followStreamingOutput = generationState is StoryGenerationState.Streaming
    val generatedTextColor = MaterialTheme.colorScheme.primary
    val isStreaming = generationState is StoryGenerationState.Streaming
            || generationState is StoryGenerationState.Preparing
            || generationState is StoryGenerationState.Applying

    // 流式生成中的呼吸光晕动效
    val infiniteTransition = rememberInfiniteTransition(label = "StoryStreamingGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "GlowAlpha",
    )

    val displayedEditedRange = document.searchRange ?: document.latestEditedRange.takeUnless {
        generationState is StoryGenerationState.Streaming
                || generationState is StoryGenerationState.Applying
    }
    val currentEditedRange by rememberUpdatedState(displayedEditedRange)
    val currentEditedStyle by rememberUpdatedState(
        newValue = SpanStyle(
            color = generatedTextColor,
            background = generatedTextColor.copy(alpha = 0.12f),
            fontWeight = FontWeight.Medium,
        ),
    )
    // 转换实例必须跨流式分片保持稳定，否则 BasicTextField 会反复重建布局状态并闪烁。
    val latestEditedOutputTransformation = remember {
        OutputTransformation {
            val editedRange = currentEditedRange
            if (
                editedRange != null
                && editedRange.start < editedRange.end
                && editedRange.end <= length
            ) {
                addStyle(
                    spanStyle = currentEditedStyle,
                    start = editedRange.start,
                    end = editedRange.end,
                )
            }
        }
    }
    LaunchedEffect(document.syncVersion) {
        if (editorState.text.toString() != document.content) {
            editorState.syncTextPreservingSelection(document.content)
        }
    }
    LaunchedEffect(document.searchRange) {
        document.searchRange?.let { range ->
            if (range.end <= editorState.text.length) editorState.edit { selection = TextRange(range.start, range.end) }
        }
    }
    LaunchedEffect(followStreamingOutput, editorScrollState) {
        if (!followStreamingOutput) return@LaunchedEffect
        // 流式文本布局完成后 maxValue 会持续增长，跟随它才能稳定滚到最新输出。
        snapshotFlow { editorScrollState.maxValue }.collect { bottom ->
            editorScrollState.scrollTo(bottom)
        }
    }
    LaunchedEffect(key1 = followStreamingOutput, key2 = markdownPreview, key3 = markdownPreviewScrollState) {
        if (!followStreamingOutput || !markdownPreview) return@LaunchedEffect
        var previousMax = markdownPreviewScrollState.maxValue
        snapshotFlow { markdownPreviewScrollState.value to markdownPreviewScrollState.maxValue }
            .collect { (value, max) ->
                val wasFollowingBottom = previousMax == 0 || value >= previousMax - 48
                if (max > previousMax && wasFollowingBottom) {
                    markdownPreviewScrollState.scrollTo(max)
                }
                previousMax = max
            }
    }
    LaunchedEffect(generationState, document.syncVersion, markdownPreview, markdownEnabled) {
        when (generationState) {
            is StoryGenerationState.Applying -> {
                // 只有成功应用的生成结果才触发自动定位。
                shouldScrollToGeneratedRange = true
                return@LaunchedEffect
            }
            is StoryGenerationState.Failed,
            is StoryGenerationState.Preparing,
            is StoryGenerationState.Streaming -> {
                shouldScrollToGeneratedRange = false
                return@LaunchedEffect
            }
            is StoryGenerationState.Idle -> Unit
        }
        if (!shouldScrollToGeneratedRange) return@LaunchedEffect

        val range = document.latestEditedRange
        val isGeneratedRangeInvalid = range == null
            || range.start < 0
            || range.start >= range.end
            || range.end > document.content.length
        if (isGeneratedRangeInvalid) {
            shouldScrollToGeneratedRange = false
            return@LaunchedEffect
        }

        val targetContent = document.content
        val targetContentLength = targetContent.length
        val targetSyncVersion = document.syncVersion
        // 文档与 TextFieldState 异步同步，等待内容及相同修订、长度的布局后再定位。
        snapshotFlow { editorState.text.toString() }.first { text -> text == targetContent }

        if (markdownEnabled && markdownPreview) {
            val targetY = snapshotFlow {
                Triple(
                    first = markdownHighlightedPositionSyncVersion,
                    second = markdownHighlightedPositionLength,
                    third = markdownHighlightedStartY,
                )
            }.first { (version, length, _) ->
                version == targetSyncVersion && length == targetContentLength
            }.third
            if (targetY != null) {
                markdownPreviewScrollState.scrollTo(
                    value = targetY.toInt().coerceIn(
                        minimumValue = 0,
                        maximumValue = markdownPreviewScrollState.maxValue,
                    ),
                )
            }
        } else {
            val textLayout = snapshotFlow {
                Triple(first = editorTextLayoutSyncVersion, second = editorTextLayoutLength, third = editorTextLayout)
            }.first { (version, length, layoutResult) ->
                version == targetSyncVersion && length == targetContentLength && layoutResult != null
            }.third
            val targetY = textLayout
                ?.getCursorRect(range.start)
                ?.top
                ?.toInt()
            if (targetY != null) {
                editorScrollState.scrollTo(targetY.coerceIn(minimumValue = 0, maximumValue = editorScrollState.maxValue))
            }
        }
        shouldScrollToGeneratedRange = false
    }
    LaunchedEffect(editorState) {
        snapshotFlow {
            StoryEditorSnapshot(
                chapterId = document.chapterId,
                content = editorState.text.toString(),
                isComposing = editorState.composition != null,
            )
        }.distinctUntilChanged().collect { snapshot ->
            StoryEditorUiIntent.EditorSnapshotChanged(snapshot).emit()
        }
    }

    PageSurface(
        kind = PageSurfaceKind.Content,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (isStreaming) 2.dp * LocalPageContentOpacity.current else 0.dp,
        border = if (isStreaming) BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha * LocalPageContentOpacity.current),
        ) else null,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageContentOpacity.current),
    ) {
        BasicTextField(
            state = editorState,
            onTextLayout = { result ->
                val range = document.searchRange
                val layout = result()
                if (layout != null && layout.layoutInput.text.text == document.content) {
                    // 生成结果定位和搜索定位共享布局回调，避免将旧布局标记为新修订。
                    editorTextLayout = layout
                    editorTextLayoutSyncVersion = document.syncVersion
                    editorTextLayoutLength = layout.layoutInput.text.length
                }
                val canScrollToSearchRange = pendingSearchScroll && range != null && layout != null && range.start < layout.layoutInput.text.length
                if (canScrollToSearchRange) {
                    pendingSearchScroll = false
                    val top = layout.getBoundingBox(range.start).top.toInt()
                    searchScope.launch { editorScrollState.scrollTo(top) }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .draggableScrollIndicator(editorScrollState),
            enabled = editable && (!markdownEnabled || !markdownPreview),
            textStyle = MaterialTheme.typography.bodyLarge.merge(
                other = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 28.sp,
                ),
            ),
            scrollState = editorScrollState,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            outputTransformation = latestEditedOutputTransformation,
            decorator = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    if (markdownEnabled && markdownPreview) {
                        val sourceText = editorState.text.toString()
                        StoryMarkdownPreview(
                            sourceText = sourceText,
                            editedRange = displayedEditedRange,
                            scrollState = markdownPreviewScrollState,
                            onEditClick = { markdownController.preview = false },
                            onHighlightedPosition = { position ->
                                // 只接受当前修订的布局，避免生成结果跳到旧坐标。
                                if (sourceText == document.content) {
                                    markdownHighlightedStartY = position
                                    markdownHighlightedPositionSyncVersion = document.syncVersion
                                    markdownHighlightedPositionLength = sourceText.length
                                }
                            },
                        )
                    } else if (markdownEnabled) {
                        // Markdown 模式下的原文编辑状态，提供右上方切回预览图标并避让留白
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 40.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                ) {
                                    if (editorState.text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.story_editor_placeholder),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                            StoryPreviewScrollButton(
                                icon = Icons.Rounded.Visibility,
                                description = R.string.confirm,
                                modifier = Modifier.align(Alignment.TopEnd),
                                onClick = returnToMarkdownPreview,
                            )
                        }
                    } else {
                        if (editorState.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.story_editor_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}

/** 等待输入法布局稳定后，按比例在原文和预览之间保持阅读位置。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SynchronizeMarkdownViewport(
    markdownPreview: Boolean,
    markdownEnabled: Boolean,
    editorScrollState: ScrollState,
    markdownPreviewScrollState: ScrollState,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisibleState = rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(markdownPreview, markdownEnabled) {
        if (!markdownEnabled) return@LaunchedEffect

        // 两种模式的内容高度不同，按滚动比例恢复视口，并等待输入法布局变化结束。
        val sourceScrollState = if (markdownPreview) {
            editorScrollState
        } else {
            markdownPreviewScrollState
        }
        val scrollFraction = if (sourceScrollState.maxValue > 0) {
            (sourceScrollState.value.toFloat() / sourceScrollState.maxValue).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f,
            )
        } else {
            0f
        }

        if (imeVisibleState.value) {
            keyboardController?.hide()
            // 输入法会改变编辑区域尺寸，通过 Compose 状态等待视口稳定。
            withTimeoutOrNull(IME_VISIBILITY_SETTLE_TIMEOUT_MILLIS.milliseconds) {
                snapshotFlow { imeVisibleState.value }.first { isVisible -> !isVisible }
            }
        } else if (!markdownPreview) {
            // 切换原文时仅展示内容，避免自动弹出键盘。
            keyboardController?.hide()
        }

        withFrameNanos { }
        withFrameNanos { }

        val targetScrollState = if (markdownPreview) {
            markdownPreviewScrollState
        } else {
            editorScrollState
        }
        targetScrollState.scrollTo(
            value = (targetScrollState.maxValue * scrollFraction)
                .toInt()
                .coerceIn(minimumValue = 0, maximumValue = targetScrollState.maxValue),
        )
    }
}

/**
 * 渲染预览、生成片段高亮及滚动入口，不持有业务偏好或访问数据仓库。
 *
 * 布局与控制：
 * - MarkdownMessageText 渲染富文本内容与生成片段高亮。
 * - 右侧悬浮工具列提供快捷编辑与长文首尾跳转操作。
 *
 * @param sourceText 章节原始正文。
 * @param editedRange 本次待高亮的生成范围。
 * @param scrollState 预览视口滚动状态。
 * @param onEditClick 切换至原文编辑模式的回调。
 * @param onHighlightedPosition 高亮文字的绝对坐标回调。
 */
@Composable
private fun StoryMarkdownPreview(
    sourceText: String,
    editedRange: StoryEditedTextRange?,
    scrollState: ScrollState,
    onEditClick: () -> Unit,
    onHighlightedPosition: (Float?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val validRange = editedRange?.takeIf { it.start >= 0 && it.start < it.end && it.end <= sourceText.length }
    val highlightedText = validRange?.let { sourceText.substring(it.start, it.end) }
    // 用出现次数匹配同文片段，避免重复段落的高亮落在第一处。
    val occurrence = if (validRange != null && !highlightedText.isNullOrBlank()) {
        MarkdownHighlightMapper.countOccurrencesBefore(
            prefix = sourceText.substring(startIndex = 0, endIndex = validRange.start),
            highlightedText = highlightedText,
        )
    } else 0
    Box(Modifier.fillMaxSize()) {
        // 正文滚动区域预留右侧留白，避免文字遮挡悬浮操作图标
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .draggableScrollIndicator(
                    state = scrollState,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    thumbThickness = 6.dp,
                )
                .verticalScroll(scrollState)
                .padding(end = 40.dp),
        ) {
            MarkdownMessageText(
                content = sourceText,
                isUser = false,
                modifier = Modifier.fillMaxWidth(),
                blockSpacing = 16.dp,
                highlightedText = highlightedText,
                highlightedOccurrenceIndex = occurrence,
                onHighlightedPosition = onHighlightedPosition,
            )
        }
        // 右侧浮动操作组，整合进入编辑与首尾跳转图标
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 顶部操作组：进入编辑模式与回到顶部
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StoryPreviewScrollButton(
                    icon = Icons.Rounded.Edit,
                    description = R.string.story_markdown_edit,
                    onClick = onEditClick,
                )
                StoryPreviewScrollButton(
                    icon = Icons.Rounded.ArrowUpward,
                    description = R.string.scroll_to_top,
                    onClick = { scope.launch { scrollState.animateScrollTo(value = 0) } },
                )
            }
            // 底部操作：直达文末
            StoryPreviewScrollButton(
                icon = Icons.Rounded.ArrowDownward,
                description = R.string.scroll_to_bottom,
                onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
            )
        }
    }
}

/**
 * 内容区悬浮操作按钮，统一样式、尺寸与可访问性描述。
 *
 * @param icon 按钮图标。
 * @param description 无障碍文本资源 ID。
 * @param modifier 外部布局修饰符。
 * @param onClick 点击事件回调。
 */
@Composable
private fun StoryPreviewScrollButton(
    icon: ImageVector,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AppSurface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 3.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(description),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val IME_VISIBILITY_SETTLE_TIMEOUT_MILLIS = 750L
