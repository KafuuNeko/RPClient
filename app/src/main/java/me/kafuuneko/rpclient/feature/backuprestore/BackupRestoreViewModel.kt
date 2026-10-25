package me.kafuuneko.rpclient.feature.backuprestore

import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreDialogState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreLoadState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiIntent
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreViewEvent
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
 * - 失败信息使用本地化资源，不向界面透传文件路径或归档内容。
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
        state.copy(loadState = BackupRestoreLoadState.Exporting, status = R.string.backup_working)
            .setup()
        // 导出允许协程取消；错误只映射为本地化状态，不能泄露归档路径。
        viewModelScope.launch {
            try {
                mRepository.exportArchive(uri)
                state.copy(status = R.string.backup_export_success).setup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.copy(status = R.string.backup_failure_detail).setup()
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
        state.copy(dialogState = BackupRestoreDialogState.ConfirmRestore).setup()
    }

    /** 用户确认后串行恢复，配置变化或页面任务取消不能中断替换收尾。 */
    @UiIntentObserver(BackupRestoreUiIntent.ConfirmRestore::class)
    private fun onConfirmRestore() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        val uri = mRestoreUri ?: return
        if (state.busy || state.dialogState != BackupRestoreDialogState.ConfirmRestore) return
        mRestoreUri = null
        state.copy(
            loadState = BackupRestoreLoadState.Restoring,
            status = R.string.backup_working,
            dialogState = BackupRestoreDialogState.None,
        ).setup()
        viewModelScope.launch {
            // 数据库替换完成后必须发出重启事件，不能恢复成可继续操作的空闲页。
            withContext(NonCancellable) {
                try {
                    mRepository.restoreArchive(uri)
                    requireRestart()
                } catch (error: Exception) {
                    if ((error as? BackupRestoreException)?.restartRequired == true) {
                        requireRestart()
                    } else {
                        BackupRestoreUiState.Normal(status = R.string.backup_failure_detail).setup()
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
        state.copy(dialogState = BackupRestoreDialogState.None).setup()
    }

    /** 操作期间不关闭页面；空闲时保留最后一帧完成退出。 */
    @UiIntentObserver(BackupRestoreUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (!state.busy) BackupRestoreUiState.Finished(state).setup()
    }

    /** 宿主重建期间可能错过一次性事件，恢复前台时再次完成待重启状态。 */
    @UiIntentObserver(BackupRestoreUiIntent.Resume::class)
    private fun onResume() {
        val state = getOrNull<BackupRestoreUiState.Normal>() ?: return
        if (state.loadState == BackupRestoreLoadState.Restarting) BackupRestoreViewEvent.RestartApp.tryEmit()
    }

    /** 关闭数据库后的终态只等待宿主重启，不再接受业务输入。 */
    private fun requireRestart() {
        BackupRestoreUiState.Normal(
            loadState = BackupRestoreLoadState.Restarting,
            status = R.string.backup_restarting,
        ).setup()
        BackupRestoreViewEvent.RestartApp.tryEmit()
    }
}
