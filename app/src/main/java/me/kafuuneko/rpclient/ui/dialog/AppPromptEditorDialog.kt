package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface

private const val SHORT_TEXT_CURSOR_END_LIMIT = 200

/**
 * 通用提示词 / 结构化文本编辑器对话框。
 *
 * 标题下方单行显示实时字数与 Token 估算，正文支持宏高亮、全屏沉浸切换、
 * 只读预览、防误触清空/恢复默认一步撤销以及必填宏内联校验提示。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppPromptEditorDialog(
    onDismissRequest: () -> Unit,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyRequest: ((String) -> Unit)? = null,
    badgeIcon: ImageVector = Icons.Rounded.AutoAwesome,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    defaultValue: String? = null,
    availableMacros: List<String> = emptyList(),
    requiredMacro: String? = null,
    errorMessage: String? = null,
    readOnly: Boolean = false,
    allowFullscreen: Boolean = true,
    editorHeightMin: Dp = 180.dp,
    editorHeightMax: Dp = 320.dp,
    placeholder: String = stringResource(R.string.prompt_editor_placeholder),
    outputTransformation: OutputTransformation = rememberPromptMacroOutputTransformation(),
    confirmText: String = stringResource(R.string.save),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
) {
    // 撤销快照仅用于清空或恢复默认后的快速撤回；手动继续输入时自动清除避免语义混淆。
    var undoSnapshot by remember { mutableStateOf<String?>(null) }
    var ignoreNextManualEdit by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val textFieldState = rememberBoundTextFieldState(value = value) { updated ->
        if (ignoreNextManualEdit) {
            ignoreNextManualEdit = false
        } else if (undoSnapshot != null) {
            undoSnapshot = null
        }
        onValueChange(updated)
    }

    // 短文本打开时定位到文末便于续写，长文本与只读模式保持在文首便于阅读。
    LaunchedEffect(textFieldState) {
        val cursorOffset =
            if (!readOnly && textFieldState.text.length <= SHORT_TEXT_CURSOR_END_LIMIT) {
                textFieldState.text.length
            } else {
                0
            }
        textFieldState.edit { selection = TextRange(cursorOffset) }
    }

    val charCount = value.length
    val estimatedTokens = (charCount / 3.5).toInt().coerceAtLeast(0)
    val statsText = stringResource(R.string.prompt_editor_char_count, charCount, estimatedTokens)
    val subtitleText = if (readOnly) {
        "${stringResource(R.string.prompt_preset_view_only_badge)} · $statsText"
    } else {
        statsText
    }

    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitleText,
        subtitleMaxLines = 1,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        compactHeader = true,
        confirmText = if (readOnly) stringResource(R.string.close) else confirmText,
        dismissText = if (readOnly) null else dismissText,
        confirmEnabled = if (readOnly) true else confirmEnabled,
        isConfirmLoading = if (readOnly) false else isConfirmLoading,
        onConfirm = if (readOnly) onDismissRequest else onConfirm,
        onDismiss = if (readOnly) null else onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = !isFullscreen),
        windowInset = if (isFullscreen) 10.dp else 0.dp,
        fillMaxHeight = isFullscreen,
    ) {
        val contentModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth()
        }
        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PromptEditorActions(
                value = value,
                defaultValue = defaultValue,
                readOnly = readOnly,
                undoAvailable = undoSnapshot != null,
                allowFullscreen = allowFullscreen,
                isFullscreen = isFullscreen,
                onUndo = {
                    val snapshot = undoSnapshot ?: return@PromptEditorActions
                    ignoreNextManualEdit = true
                    undoSnapshot = null
                    textFieldState.setTextAndPlaceCursorAtEnd(snapshot)
                },
                onClear = {
                    undoSnapshot = value
                    ignoreNextManualEdit = true
                    textFieldState.setTextAndPlaceCursorAtEnd("")
                },
                onCopyRequest = onCopyRequest,
                onRestoreDefault = { restored ->
                    undoSnapshot = value
                    ignoreNextManualEdit = true
                    textFieldState.setTextAndPlaceCursorAtEnd(restored)
                },
                onToggleFullscreen = { isFullscreen = !isFullscreen },
            )
            PromptEditorViewport(
                modifier = Modifier.weight(weight = 1f, fill = isFullscreen),
                state = textFieldState,
                placeholder = placeholder,
                outputTransformation = outputTransformation,
                readOnly = readOnly,
                hasError = !errorMessage.isNullOrBlank(),
                isFullscreen = isFullscreen,
                editorHeightMin = editorHeightMin,
                editorHeightMax = editorHeightMax,
            )
            if (!errorMessage.isNullOrBlank()) {
                PromptEditorErrorBanner(message = errorMessage)
            }
            PromptMacroChips(
                macros = availableMacros,
                requiredMacro = requiredMacro,
                readOnly = readOnly,
                onInsert = { macro -> textFieldState.insertMacro(macro) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptEditorActions(
    value: String,
    defaultValue: String?,
    readOnly: Boolean,
    undoAvailable: Boolean,
    allowFullscreen: Boolean,
    isFullscreen: Boolean,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onCopyRequest: ((String) -> Unit)?,
    onRestoreDefault: (String) -> Unit,
    onToggleFullscreen: () -> Unit,
) {
    val canRestoreDefault = !readOnly && defaultValue != null && value != defaultValue
    val canClear = !readOnly && value.isNotEmpty()
    val canCopy = value.isNotEmpty() && onCopyRequest != null
    val hasAnyAction = undoAvailable || canRestoreDefault || canClear || canCopy || allowFullscreen
    if (!hasAnyAction) return

    val haptic = LocalHapticFeedback.current
    // 左右分区隔开“回退/清空”与“复制/全屏”，降低高频复制时误点清空的概率。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 文字操作在剩余空间换行，为右侧固定尺寸图标保留可点击区域。
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (undoAvailable) {
                ToolbarPillAction(
                    icon = Icons.AutoMirrored.Rounded.Undo,
                    text = stringResource(R.string.prompt_editor_undo),
                    highlighted = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onUndo()
                    },
                )
            }
            if (canRestoreDefault) {
                ToolbarPillAction(
                    icon = Icons.Rounded.RestartAlt,
                    text = stringResource(R.string.prompt_editor_restore_default),
                    highlighted = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRestoreDefault(defaultValue)
                    },
                )
            }
        }

        if (canClear) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClear()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.prompt_editor_clear),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.80f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (canCopy) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCopyRequest.invoke(value)
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (allowFullscreen) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFullscreen()
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isFullscreen) {
                        Icons.Rounded.FullscreenExit
                    } else {
                        Icons.Rounded.Fullscreen
                    },
                    contentDescription = stringResource(
                        if (isFullscreen) {
                            R.string.prompt_editor_exit_fullscreen
                        } else {
                            R.string.fullscreen_editor
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolbarPillAction(
    icon: ImageVector,
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    // 高亮态用于强调刚触发清空或重置后的撤销入口，非高亮态承接恢复默认等常规动作。
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = dialogControlColor(containerColor),
        border = if (highlighted) {
            BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
            )
        } else {
            null
        },
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PromptEditorViewport(
    state: TextFieldState,
    placeholder: String,
    outputTransformation: OutputTransformation,
    readOnly: Boolean,
    hasError: Boolean,
    isFullscreen: Boolean,
    editorHeightMin: Dp,
    editorHeightMax: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        hasError -> MaterialTheme.colorScheme.error
        isFocused && !readOnly -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    }
    val borderWidth = if (hasError || (isFocused && !readOnly)) 1.5.dp else 1.dp
    val heightModifier = if (isFullscreen) {
        Modifier.fillMaxHeight()
    } else {
        Modifier.heightIn(min = editorHeightMin, max = editorHeightMax)
    }

    // 滚动与光标避让使用文本框同一份状态，不能再套 verticalScroll 争抢可见区域。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(heightModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        if (state.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
        BasicTextField(
            state = state,
            readOnly = readOnly,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                .draggableScrollIndicator(scrollState),
            scrollState = scrollState,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            ),
            outputTransformation = outputTransformation,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun PromptEditorErrorBanner(message: String) {
    // 在编辑框下方即时展示校验错误，避免依赖保存失败后的短暂 Toast。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptMacroChips(
    macros: List<String>,
    requiredMacro: String?,
    readOnly: Boolean,
    onInsert: (String) -> Unit,
) {
    if (macros.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    // 必填宏使用高亮边框与星标提示；只读预览下仅展示宏集合，不执行插入。
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.prompt_editor_macro_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
            )
            if (!requiredMacro.isNullOrBlank()) {
                Text(
                    text = stringResource(
                        R.string.prompt_preset_required_macro_hint,
                        requiredMacro
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            macros.forEach { macro ->
                val isRequired = macro == requiredMacro
                val chipModifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .let { base ->
                        if (readOnly) {
                            base
                        } else {
                            base.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onInsert(macro)
                            }
                        }
                    }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRequired) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                    },
                    border = BorderStroke(
                        width = if (isRequired) 1.2.dp else 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isRequired) 0.55f else 0.25f,
                        ),
                    ),
                    modifier = chipModifier,
                ) {
                    Text(
                        text = if (isRequired) "$macro *" else macro,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun TextFieldState.insertMacro(macro: String) {
    // 直接更新原始选择区，让宏插入与普通输入共用文本和光标状态。
    edit {
        val start = selection.min
        val end = selection.max
        val needsLeadingNewline = start > 0 && asCharSequence()[start - 1] != '\n'
        val insertion = when {
            macro != "<START>" -> macro
            needsLeadingNewline -> "\n<START>\n"
            else -> "<START>\n"
        }
        replace(start = start, end = end, text = insertion)
        selection = TextRange(start + insertion.length)
    }
}

@Preview(name = "AppPromptEditorDialog Preview", showBackground = true)
@Composable
private fun AppPromptEditorDialogPreview() {
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppPromptEditorDialog(
                onDismissRequest = {},
                title = "主提示词",
                value = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.",
                defaultValue = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.\nWrite one reply only.",
                availableMacros = listOf("{{char}}", "{{user}}"),
                onValueChange = {},
                onConfirm = {},
            )
        }
    }
}
