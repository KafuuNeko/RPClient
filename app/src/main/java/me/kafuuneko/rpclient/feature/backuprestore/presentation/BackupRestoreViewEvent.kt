package me.kafuuneko.rpclient.feature.backuprestore.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 仅由 Activity 执行的文件选择、复制和进程重启动作。 */
sealed interface BackupRestoreViewEvent : IViewEvent {
    data object CreateArchive : BackupRestoreViewEvent

    data object OpenArchive : BackupRestoreViewEvent

    data object RestartApp : BackupRestoreViewEvent

    data class CopyDetails(val detail: String) : BackupRestoreViewEvent
}
