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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.rememberBoundTextFieldState
import me.kafuuneko.rpclient.utils.rememberPromptMacroOutputTransformation
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface

/**
 * 通用提示词 / 结构化文本编辑器对话框。
 *
 * 支持实时字数与 Token 估算、宏语法高亮、清空、请求复制、恢复默认预设及快速插入。
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
    subtitle: String? = null,
    badgeIcon: ImageVector = Icons.Rounded.AutoAwesome,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    defaultValue: String? = null,
    availableMacros: List<String> = emptyList(),
    editorHeightMin: Dp = 180.dp,
    editorHeightMax: Dp = 320.dp,
    placeholder: String = stringResource(R.string.prompt_editor_placeholder),
    outputTransformation: OutputTransformation = rememberPromptMacroOutputTransformation(),
    confirmText: String = stringResource(R.string.save),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
) {
    val textFieldState = rememberBoundTextFieldState(value, onValueChange)
    // 保留首次打开时从文末开始编辑的行为，后续外部更新由绑定组件保留选择区。
    LaunchedEffect(textFieldState) {
        textFieldState.edit { selection = TextRange(length) }
    }

    val charCount = value.length
    val estimatedTokens = (charCount / 3.5).toInt().coerceAtLeast(0)

    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        compactHeader = true,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        onConfirm = onConfirm,
        onDismiss = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PromptEditorToolbar(
                value = value,
                charCount = charCount,
                estimatedTokens = estimatedTokens,
                defaultValue = defaultValue,
                onClear = { textFieldState.setTextAndPlaceCursorAtEnd("") },
                onCopyRequest = onCopyRequest,
                onRestoreDefault = { restored ->
                    textFieldState.setTextAndPlaceCursorAtEnd(restored)
                },
            )
            PromptEditorViewport(
                modifier = Modifier.weight(weight = 1f, fill = false),
                state = textFieldState,
                placeholder = placeholder,
                outputTransformation = outputTransformation,
                editorHeightMin = editorHeightMin,
                editorHeightMax = editorHeightMax,
            )
            PromptMacroChips(
                macros = availableMacros,
                onInsert = { macro -> textFieldState.insertMacro(macro) },
            )
        }
    }
}

@Composable
private fun PromptEditorToolbar(
    value: String,
    charCount: Int,
    estimatedTokens: Int,
    defaultValue: String?,
    onClear: () -> Unit,
    onCopyRequest: ((String) -> Unit)?,
    onRestoreDefault: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        ) {
            Text(
                text = stringResource(
                    R.string.prompt_editor_char_count,
                    charCount,
                    estimatedTokens
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        PromptEditorActions(
            value = value,
            defaultValue = defaultValue,
            onClear = onClear,
            onCopyRequest = onCopyRequest,
            onRestoreDefault = onRestoreDefault,
        )
    }
}

@Composable
private fun PromptEditorActions(
    value: String,
    defaultValue: String?,
    onClear: () -> Unit,
    onCopyRequest: ((String) -> Unit)?,
    onRestoreDefault: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (value.isNotEmpty()) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClear()
                },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.prompt_editor_clear),
                    modifier = Modifier.size(16.dp),
                )
            }
            if (onCopyRequest != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCopyRequest(value)
                    },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (!defaultValue.isNullOrBlank() && value != defaultValue) {
            RestoreDefaultAction(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onRestoreDefault(defaultValue)
                },
            )
        }
    }
}

@Composable
private fun RestoreDefaultAction(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.prompt_editor_restore_default),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun PromptEditorViewport(
    state: TextFieldState,
    placeholder: String,
    outputTransformation: OutputTransformation,
    editorHeightMin: Dp,
    editorHeightMax: Dp,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    // 滚动与光标避让使用文本框同一份状态，不能再套 verticalScroll 争抢可见区域。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = editorHeightMin, max = editorHeightMax)
            .clip(RoundedCornerShape(16.dp))
            .background(dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
    ) {
        if (state.text.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }
        BasicTextField(
            state = state,
            modifier = Modifier
                .fillMaxSize()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromptMacroChips(
    macros: List<String>,
    onInsert: (String) -> Unit,
) {
    if (macros.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.prompt_editor_macro_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            macros.forEach { macro ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onInsert(macro)
                            },
                        ),
                ) {
                    Text(
                        text = macro,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
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
                subtitle = "普通对话生成时的全局核心系统指令",
                value = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.",
                defaultValue = "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.\nWrite one reply only.",
                availableMacros = listOf("{{char}}", "{{user}}"),
                onValueChange = {},
                onConfirm = {},
            )
        }
    }
}
