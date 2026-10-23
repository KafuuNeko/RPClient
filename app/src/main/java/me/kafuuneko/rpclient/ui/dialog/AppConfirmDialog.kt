package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

/**
 * 通用确认对话框。
 *
 * 用于引导用户进行常规操作确认或重要前置选择。
 * @param properties 窗口交互规则；需要用户明确处理的失败提示可禁止外侧点击关闭。
 */
@Composable
fun AppConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    badgeIcon: ImageVector = Icons.AutoMirrored.Rounded.HelpOutline,
    badgeTone: DialogBadgeTone = DialogBadgeTone.Primary,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    confirmIsDestructive: Boolean = false,
    stackButtons: Boolean? = null,
    onConfirm: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AppDialogScaffold(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = message,
        badgeIcon = badgeIcon,
        badgeTone = badgeTone,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        confirmIsDestructive = confirmIsDestructive,
        stackButtons = stackButtons,
        onConfirm = onConfirm,
        properties = properties,
        content = content,
    )
}

/**
 * 危险/破坏性操作确认对话框。
 *
 * 预设红色警告徽标、错误容器色调与长按震动反馈，常用于删除实体、清空数据或放弃未保存变更。
 */
@Composable
fun AppDangerDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    badgeIcon: ImageVector = Icons.Rounded.DeleteOutline,
    confirmText: String = stringResource(R.string.delete),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    stackButtons: Boolean? = null,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AppConfirmDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        message = message,
        badgeIcon = badgeIcon,
        badgeTone = DialogBadgeTone.Danger,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        confirmIsDestructive = true,
        stackButtons = stackButtons,
        onConfirm = onConfirm,
        content = content,
    )
}

/**
 * 警示对话框（黄色/三级强调色徽标），常用于预算不足提示、可能引起兼容性变化的非破坏性确认。
 */
@Composable
fun AppWarningDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    badgeIcon: ImageVector = Icons.Rounded.WarningAmber,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String? = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    isConfirmLoading: Boolean = false,
    stackButtons: Boolean? = null,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AppConfirmDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        message = message,
        badgeIcon = badgeIcon,
        badgeTone = DialogBadgeTone.Warning,
        confirmText = confirmText,
        dismissText = dismissText,
        confirmEnabled = confirmEnabled,
        isConfirmLoading = isConfirmLoading,
        confirmIsDestructive = false,
        stackButtons = stackButtons,
        onConfirm = onConfirm,
        content = content,
    )
}

@Preview(name = "AppConfirmDialog - Normal", showBackground = true)
@Composable
private fun AppConfirmDialogPreview() {
    AppTheme(dynamicColor = false) {
        AppConfirmDialog(
            onDismissRequest = {},
            title = "同步云端预设",
            message = "是否确认下载并覆盖当前本地同名预设？",
            onConfirm = {},
        )
    }
}

@Preview(name = "AppDangerDialog - Destructive", showBackground = true)
@Composable
private fun AppDangerDialogPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppDangerDialog(
                onDismissRequest = {},
                title = "删除世界书词条",
                message = "确定要删除词条「艾尔登法环」吗？此操作无法撤销。",
                onConfirm = {},
            )
        }
    }
}
