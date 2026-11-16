package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.utils.rememberJsonSyntaxOutputTransformation

/**
 * 结构化文本/代码编辑对话框。
 *
 * 适用于 JSON 补丁、自定义请求头等需要等宽字体、语法高亮与横纵向滚动的场景。
 * @param state 调用方持有的编辑草稿，确认时读取当前文本。
 */
@Composable
fun AppCodeEditorDialog(
    onDismissRequest: () -> Unit,
    title: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    editorHeight: Dp = 230.dp,
    badgeIcon: ImageVector = Icons.Rounded.Code,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    outputTransformation: OutputTransformation = rememberJsonSyntaxOutputTransformation(),
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    onConfirm: () -> Unit,
) {
    // 代码视口参与剩余高度分配，键盘展开时为标题、说明和操作按钮保留空间。
    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        compactHeader = true,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        onConfirm = onConfirm,
    ) {
        val verticalScroll = rememberScrollState()
        val shape = RoundedCornerShape(16.dp)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(editorHeight)
                .clip(shape)
                .background(dialogControlColor(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = shape,
                )
                .padding(12.dp)
                .draggableScrollIndicator(verticalScroll),
        ) {
            // 外层只处理长行的横向滚动，滑块和光标共用文本框实际的纵向滚动范围。
            BasicTextField(
                state = state,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .widthIn(min = maxWidth)
                    .fillMaxHeight(),
                scrollState = verticalScroll,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                outputTransformation = outputTransformation,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Preview(name = "AppCodeEditorDialog - Dark", showBackground = true)
@Composable
private fun AppCodeEditorDialogPreview() {
    val state = remember { TextFieldState("{\n  \"temperature\": 0.8,\n  \"top_p\": 0.95\n}") }
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppCodeEditorDialog(
                onDismissRequest = {},
                title = "自定义请求头 JSON",
                state = state,
                onConfirm = {},
            )
        }
    }
}
