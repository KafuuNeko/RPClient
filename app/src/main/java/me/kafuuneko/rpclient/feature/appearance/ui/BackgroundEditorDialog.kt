package me.kafuuneko.rpclient.feature.appearance.ui

import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.AppearanceUiIntent
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorDialogState
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorState
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** 使用应用统一的动作列表弹窗呈现保存、放弃和取消，草稿处理仍由 Intent 驱动。 */
@Composable
internal fun BackgroundEditorDialog(state: BackgroundEditorState, emit: (AppearanceUiIntent) -> Unit) {
    if (state.dialogState != BackgroundEditorDialogState.UnsavedChanges) return
    // 复用统一容器、动作样式及多语言排版，导入期间仅禁用保存入口。
    AppActionListDialog(
        onDismissRequest = { emit(AppearanceUiIntent.DismissDialog) },
        title = stringResource(R.string.appearance_save_changes_title),
        subtitle = stringResource(R.string.appearance_save_changes_message),
        badgeIcon = Icons.Rounded.Save,
        dismissText = stringResource(R.string.cancel),
        actions = listOf(
            AppActionItem(
                icon = Icons.Rounded.Save,
                title = stringResource(R.string.save),
                enabled = !state.working,
                onClick = { emit(AppearanceUiIntent.ApplyBackground) }
            ),
            AppActionItem(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.discard_changes),
                isDestructive = true,
                onClick = { emit(AppearanceUiIntent.DiscardBackgroundChanges) }
            )
        )
    )
}

@Preview(name = "Background changes - Light")
@Preview(name = "Background changes - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackgroundEditorDialogPreview() {
    AppTheme(dynamicColor = false) {
        BackgroundEditorDialog(
            BackgroundEditorState(
                settings = PageBackground(BackgroundPage.Chat),
                dialogState = BackgroundEditorDialogState.UnsavedChanges
            )
        ) {}
    }
}
