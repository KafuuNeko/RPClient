package me.kafuuneko.rpclient.feature.backuprestore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiIntent
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppSurface

/** 备份页面只渲染状态并发送操作，文件选择和数据库读写均由宿主与 ViewModel 执行。 */
@Composable
fun BackupRestoreLayout(state: BackupRestoreUiState, emit: BackupRestoreUiIntent.() -> Unit) {
    BackHandler { BackupRestoreUiIntent.Back.emit() }
    when (state) {
        is BackupRestoreUiState.Normal -> {
            BackupContent(state, emit)
            DialogSwitch(state, emit)
        }
        is BackupRestoreUiState.Finished -> BackupContent(state.previous) { }
    }
}

/** 长说明与操作栏共同滚动，保证小屏和大字体下仍可操作。 */
@Composable
private fun BackupContent(state: BackupRestoreUiState.Normal, emit: BackupRestoreUiIntent.() -> Unit) {
    AppSurface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.backup_restore_description), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.backup_restore_security_warning),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            // 操作期间显示持续进度并统一禁用重复操作。
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Button(modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                onClick = { BackupRestoreUiIntent.ExportClick.emit() }) {
                Text(stringResource(R.string.backup_export_action))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                onClick = { BackupRestoreUiIntent.RestoreClick.emit() }) {
                Text(stringResource(R.string.backup_restore_action))
            }
            state.status?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            TextButton(modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                onClick = { BackupRestoreUiIntent.Back.emit() }) { Text(stringResource(R.string.backup_done)) }
        }
    }
}

/** 覆盖恢复复用全局危险操作弹窗，取消时不保留 URI 选择。 */
@Composable
private fun DialogSwitch(state: BackupRestoreUiState.Normal, emit: BackupRestoreUiIntent.() -> Unit) {
    when (state.dialog) {
        BackupDialogState.None -> Unit
        BackupDialogState.ConfirmRestore -> AppDangerDialog(
            onDismissRequest = { BackupRestoreUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.backup_restore_confirm_title),
            message = stringResource(R.string.backup_restore_confirm_message),
            confirmText = stringResource(R.string.backup_restore_action),
            confirmEnabled = !state.busy,
            onConfirm = { BackupRestoreUiIntent.ConfirmRestore.emit() }
        )
    }
}

@Preview
@Composable
private fun BackupRestorePreview() {
    AppTheme(dynamicColor = false) { BackupRestoreLayout(BackupRestoreUiState.Normal()) { } }
}
