package me.kafuuneko.rpclient.feature.backuprestore

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupDialogState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupOperation
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiIntent
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreViewEvent
import me.kafuuneko.rpclient.libs.backup.BackupReport
import me.kafuuneko.rpclient.libs.backup.BackupRestoreException
import me.kafuuneko.rpclient.libs.backup.PortableBackupRepository
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 备份与恢复的 MVI 状态持有者。
 * - 导入 URI 作为私有快照，配置变化不会丢失运行中的操作或弹窗。
 * - 恢复进入文件替换后必须完成收尾；数据库关闭后只允许重启。
 * - 状态文案使用本地化资源；可复制详情只含阶段、数量和安全错误摘要。
 */
class BackupRestoreViewModel : CoreViewModelWithEvent<BackupRestoreUiIntent, BackupRestoreUiState>(
    BackupRestoreUiState.Normal()
), KoinComponent {
    private val mRepository by inject<PortableBackupRepository>()
    private var mRestoreUri: Uri? = null

    /** 空闲时请求系统创建归档文件。 */
    @UiIntentObserver(BackupRestoreUiIntent.ExportClick::class)
    private fun onExportClick() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (!state.busy) BackupRestoreViewEvent.CreateArchive.tryEmit()
    }

    /** 取消系统选择不启动任务，选择成功后由页面任务执行导出。 */
    @UiIntentObserver(BackupRestoreUiIntent.ExportResult::class)
    private fun onExportResult(intent: BackupRestoreUiIntent.ExportResult) {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        val uri = intent.uri ?: return
        if (state.busy) return
        state.copy(operation = BackupOperation.Exporting, status = R.string.backup_working).setup()
        // 导出允许协程取消；失败详情只接受报告生成的安全摘要。
        viewModelScope.launch {
            val report = BackupReport()
            try {
                mRepository.exportArchive(uri, report)
                state.copy(status = R.string.backup_export_success, detail = report.text()).setup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.copy(
                    status = R.string.backup_failure_detail,
                    detail = report.textWithError(error)
                ).setup()
            }
        }
    }

    /** 空闲时请求系统选择归档，实际恢复仍需二次确认。 */
    @UiIntentObserver(BackupRestoreUiIntent.RestoreClick::class)
    private fun onRestoreClick() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (!state.busy) BackupRestoreViewEvent.OpenArchive.tryEmit()
    }

    /** 仅保存选择结果并显示确认弹窗，不在文件选择回调中修改数据。 */
    @UiIntentObserver(BackupRestoreUiIntent.RestoreResult::class)
    private fun onRestoreResult(intent: BackupRestoreUiIntent.RestoreResult) {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (state.busy || intent.uri == null) return
        mRestoreUri = intent.uri
        state.copy(dialog = BackupDialogState.ConfirmRestore).setup()
    }

    /** 用户确认后串行恢复，配置变化或页面任务取消不能中断替换收尾。 */
    @UiIntentObserver(BackupRestoreUiIntent.ConfirmRestore::class)
    private fun onConfirmRestore() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        val uri = mRestoreUri ?: return
        if (state.busy || state.dialog != BackupDialogState.ConfirmRestore) return
        mRestoreUri = null
        state.copy(
            operation = BackupOperation.Restoring, status = R.string.backup_working,
            dialog = BackupDialogState.None
        ).setup()
        viewModelScope.launch {
            // 替换进入不可取消的收尾阶段；成功或关闭数据库后的失败都进入待重启状态。
            withContext(NonCancellable) {
                val report = BackupReport()
                try {
                    mRepository.restoreArchive(uri, report)
                    requireRestart(R.string.backup_restore_done, report.text())
                } catch (error: Exception) {
                    if ((error as? BackupRestoreException)?.restartRequired == true) {
                        requireRestart(
                            R.string.backup_restart_required,
                            report.textWithError(error)
                        )
                    } else {
                        BackupRestoreUiState.Normal(
                            status = R.string.backup_failure_detail,
                            detail = report.textWithError(error)
                        ).setup()
                    }
                }
            }
        }
    }

    /** 撤销未执行的恢复选择。 */
    @UiIntentObserver(BackupRestoreUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (state.busy) return
        mRestoreUri = null
        state.copy(dialog = BackupDialogState.None).setup()
    }

    /** 操作期间不关闭页面；空闲时保留最后一帧完成退出。 */
    @UiIntentObserver(BackupRestoreUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (!state.busy) BackupRestoreUiState.Finished(state).setup()
    }

    /** 用户读完详情后手动重启；自动重启会让恢复警告随进程消失。 */
    @UiIntentObserver(BackupRestoreUiIntent.RestartClick::class)
    private fun onRestartClick() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (state.operation == BackupOperation.Restarting) BackupRestoreViewEvent.RestartApp.tryEmit()
    }

    /** 通过宿主复制当前摘要，避免 Compose 直接访问剪贴板或发送提示。 */
    @UiIntentObserver(BackupRestoreUiIntent.CopyDetailsClick::class)
    private fun onCopyDetailsClick() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        val detail = state.detail?.takeIf { it.isNotBlank() } ?: return
        BackupRestoreViewEvent.CopyDetails(detail).tryEmit()
    }

    /** 关闭数据库后的终态只等待用户重启，不再接受业务输入。 */
    private fun requireRestart(@StringRes status: Int, detail: String) {
        BackupRestoreUiState.Normal(
            operation = BackupOperation.Restarting,
            status = status,
            detail = detail
        ).setup()
    }
}
