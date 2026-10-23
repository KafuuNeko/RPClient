package me.kafuuneko.rpclient.feature.backuprestore.presentation

import android.net.Uri

/** 备份页面的用户操作与系统文件选择结果。 */
sealed interface BackupRestoreUiIntent {
    data object ExportClick : BackupRestoreUiIntent

    data class ExportResult(val uri: Uri?) : BackupRestoreUiIntent

    data object RestoreClick : BackupRestoreUiIntent

    data class RestoreResult(val uri: Uri?) : BackupRestoreUiIntent

    data object ConfirmRestore : BackupRestoreUiIntent

    data object DismissDialog : BackupRestoreUiIntent

    data object Back : BackupRestoreUiIntent

    data object Resume : BackupRestoreUiIntent
}
