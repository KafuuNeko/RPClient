package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme

/** 确认删除已选择会话的通用展示对话框。 */
@Composable
fun DeleteSelectedSessionsDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDangerDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.delete_selected_sessions_title),
        message = stringResource(R.string.delete_selected_sessions_message, count),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirm,
    )
}

@Preview(name = "DeleteSelectedSessionsDialog Preview", showBackground = true)
@Composable
private fun DeleteSelectedSessionsDialogPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            DeleteSelectedSessionsDialog(
                count = 3,
                onConfirm = {},
                onDismiss = {},
            )
        }
    }
}
