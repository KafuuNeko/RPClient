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
import me.kafuuneko.rpclient.feature.appearance.presentation.BackgroundEditorLoadState
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** 使用应用统一的动作列表弹窗呈现保存、放弃和取消，草稿处理仍由 Intent 驱动。 */
@Composable
internal fun BackgroundEditorDialogSwitch(
    dialogState: BackgroundEditorDialogState,
    loadState: BackgroundEditorLoadState,
    emit: (AppearanceUiIntent) -> Unit
) {
    // 仅在可交互阶段显示草稿确认，任务反馈由加载分发器负责。
    if (loadState.blocksEditing || dialogState != BackgroundEditorDialogState.UnsavedChanges) return
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
                onClick = { emit(AppearanceUiIntent.ApplyBackground) },
            ),
            AppActionItem(
                icon = Icons.Rounded.DeleteOutline,
                title = stringResource(R.string.discard_changes),
                isDestructive = true,
                onClick = { emit(AppearanceUiIntent.DiscardBackgroundChanges) },
            ),
        ),
    )
}

/** 按互斥任务阶段分发加载反馈，预览渲染沿用页面内的进度提示。 */
@Composable
internal fun BackgroundEditorLoadSwitch(
    state: BackgroundEditorLoadState,
    emit: (AppearanceUiIntent) -> Unit
) {
    // 阻塞任务使用弹窗层，避免页面滚动后看不到加载反馈。
    when (state) {
        BackgroundEditorLoadState.None, BackgroundEditorLoadState.Rendering -> Unit
        BackgroundEditorLoadState.Loading, BackgroundEditorLoadState.Saving -> LoadingDialog(
            title = stringResource(R.string.loading),
            description = stringResource(R.string.appearance_background_working),
        )

        BackgroundEditorLoadState.ImportingImage -> LoadingDialog(
            title = stringResource(R.string.loading),
            description = stringResource(R.string.image_loading),
            onCancel = { emit(AppearanceUiIntent.CancelImageImport) },
            cancelOnBackPress = true,
        )
    }
}

@Preview(name = "Background changes - Light")
@Preview(name = "Background changes - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackgroundEditorDialogPreview() {
    AppTheme(dynamicColor = false) {
        BackgroundEditorDialogSwitch(
            dialogState = BackgroundEditorDialogState.UnsavedChanges,
            loadState = BackgroundEditorLoadState.None,
            emit = {},
        )
    }
}
