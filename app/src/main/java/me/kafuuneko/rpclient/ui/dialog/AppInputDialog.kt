package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField

/**
 * 现代化通用文本输入对话框。
 *
 * 支持单行/多行编辑、自动焦点聚焦、一键清空、密码掩码切换与键盘 Done 快捷提交。
 */
@Composable
fun AppInputDialog(
    onDismissRequest: () -> Unit,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    password: Boolean = false,
    autoFocus: Boolean = true,
    clearable: Boolean = true,
    enabled: Boolean = true,
    badgeIcon: ImageVector = Icons.Rounded.Edit,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = value.isNotBlank(),
    isConfirmLoading: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }

    if (autoFocus && enabled) {
        LaunchedEffect(enabled) {
            focusRequester.requestFocus()
        }
    }

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
        onConfirm = onConfirm
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (password) {
                // 密码掩码仍使用值式 API；密码输入固定为单行，不需要纵向指示器。
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = if (label != null) { { Text(label) } } else null,
                    placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
                    singleLine = true,
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (enabled && confirmEnabled && !isConfirmLoading) onConfirm()
                        }
                    ),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            } else {
                RpScrollableOutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = if (label != null) { { Text(label) } } else null,
                    placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
                    singleLine = singleLine,
                    minLines = minLines,
                    maxLines = maxLines,
                    enabled = enabled,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                    ),
                    onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
                        if (enabled && confirmEnabled && !isConfirmLoading) {
                            onConfirm()
                        } else {
                            performDefaultAction()
                        }
                    },
                    trailingIcon = if (clearable && value.isNotEmpty()) {
                        {
                            IconButton(onClick = { onValueChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            extraContent()
        }
    }
}

@Preview(name = "AppInputDialog - Light", showBackground = true)
@Composable
private fun AppInputDialogPreview() {
    var text by remember { mutableStateOf("未命名故事") }
    AppTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppInputDialog(
                onDismissRequest = {},
                title = "修改故事标题",
                value = text,
                onValueChange = { text = it },
                label = "故事标题",
                placeholder = "请输入标题...",
                onConfirm = {}
            )
        }
    }
}
