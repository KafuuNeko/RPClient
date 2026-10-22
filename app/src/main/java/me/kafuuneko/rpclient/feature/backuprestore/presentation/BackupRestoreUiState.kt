package me.kafuuneko.rpclient.feature.backuprestore.presentation

import androidx.annotation.StringRes

/** 备份页面状态；文件 URI 留在 ViewModel，界面只接收进度与本地化结果。 */
sealed interface BackupRestoreUiState {
    data class Normal(
        val operation: BackupOperation = BackupOperation.Idle,
        @param:StringRes val status: Int? = null,
        val dialog: BackupDialogState = BackupDialogState.None
    ) : BackupRestoreUiState {
        val busy: Boolean get() = operation != BackupOperation.Idle
    }

    data class Finished(val previous: Normal) : BackupRestoreUiState
}

/** 数据库关闭后保持重启状态，不能继续发起读写。 */
enum class BackupOperation { Idle, Exporting, Restoring, Restarting }

/** 页面互斥弹窗状态，覆盖恢复必须先明确确认。 */
enum class BackupDialogState { None, ConfirmRestore }
