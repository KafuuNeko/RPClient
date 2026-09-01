package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.utils.rememberJsonSyntaxVisualTransformation

/**
 * 结构化文本/代码编辑对话框。
 *
 * 适用于 JSON 补丁、自定义请求头、Prompt 模板等需要等宽字体、语法高亮与横纵向滚动的场景。
 */
@Composable
fun AppCodeEditorDialog(
    onDismissRequest: () -> Unit,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    editorNote: String? = null,
    editorHeight: Dp = 230.dp,
    badgeIcon: ImageVector = Icons.Rounded.Code,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    visualTransformation: VisualTransformation = rememberJsonSyntaxVisualTransformation(),
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    onConfirm: () -> Unit
) {
    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        onConfirm = onConfirm
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!editorNote.isNullOrBlank()) {
                Text(
                    text = editorNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(editorHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val horizontalPadding = 24.dp
                    val verticalPadding = 24.dp
                    val minimumContentWidth = (maxWidth - horizontalPadding).coerceAtLeast(0.dp)
                    val minimumContentHeight = (maxHeight - verticalPadding).coerceAtLeast(0.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .draggableScrollIndicator(verticalScrollState)
                            .horizontalScroll(horizontalScrollState)
                            .verticalScroll(verticalScrollState)
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .widthIn(min = minimumContentWidth)
                                .heightIn(min = minimumContentHeight),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            visualTransformation = visualTransformation,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "AppCodeEditorDialog - Dark", showBackground = true)
@Composable
private fun AppCodeEditorDialogPreview() {
    var code by remember { mutableStateOf("{\n  \"temperature\": 0.8,\n  \"top_p\": 0.95\n}") }
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppCodeEditorDialog(
                onDismissRequest = {},
                title = "自定义请求头 JSON",
                editorNote = "请以合法的 JSON 格式输入请求头键值对。",
                value = code,
                onValueChange = { code = it },
                onConfirm = {}
            )
        }
    }
}
