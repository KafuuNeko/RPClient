package me.kafuuneko.rpclient.feature.backuprestore.presentation

import androidx.annotation.StringRes

/** 备份页面状态；文件 URI 留在 ViewModel，界面只接收进度与本地化结果。 */
sealed interface BackupRestoreUiState {
    data class Normal(
        val loadState: BackupRestoreLoadState = BackupRestoreLoadState.None,
        @param:StringRes val status: Int? = null,
        val dialogState: BackupRestoreDialogState = BackupRestoreDialogState.None
    ) : BackupRestoreUiState {
        val busy: Boolean get() = loadState != BackupRestoreLoadState.None
    }

    data class Finished(val previous: Normal) : BackupRestoreUiState
}

/** 数据库关闭后保持重启状态，不能继续发起读写。 */
sealed class BackupRestoreLoadState {
    data object None : BackupRestoreLoadState()

    data object Exporting : BackupRestoreLoadState()

    data object Restoring : BackupRestoreLoadState()

    data object Restarting : BackupRestoreLoadState()
}

/** 页面互斥弹窗状态，覆盖恢复必须先明确确认。 */
sealed class BackupRestoreDialogState {
    data object None : BackupRestoreDialogState()

    data object ConfirmRestore : BackupRestoreDialogState()
}
