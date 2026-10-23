package me.kafuuneko.rpclient.feature.backuprestore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiIntent
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreUiState
import me.kafuuneko.rpclient.feature.backuprestore.presentation.BackupRestoreViewEvent
import me.kafuuneko.rpclient.feature.backuprestore.ui.BackupRestoreLayout
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 备份页面宿主，只处理系统文件选择、状态绑定和进程重启。 */
class BackupRestoreActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<BackupRestoreViewModel>()
    private val mExportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            mViewModel.emit(BackupRestoreUiIntent.ExportResult(uri))
        }
    private val mImportLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            mViewModel.emit(BackupRestoreUiIntent.RestoreResult(uri))
        }

    /** 提供文件选择与重启事件流。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 配置变化后补办尚未完成的重启动作。 */
    override fun onResume() {
        super.onResume()
        mViewModel.emit(BackupRestoreUiIntent.Resume)
    }

    /** 绑定页面状态，完成退出时保留最后一个可渲染状态。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is BackupRestoreUiState.Finished) finish() }
        BackupRestoreLayout(state, mViewModel::emit)
    }

    /** 分发只能由 Activity 执行的系统动作。 */
    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            BackupRestoreViewEvent.CreateArchive -> mExportLauncher.launch("RPClient-Portable-Data.zip")
            BackupRestoreViewEvent.OpenArchive -> mImportLauncher.launch(
                input = arrayOf(
                    "application/zip",
                    "application/octet-stream",
                ),
            )

            BackupRestoreViewEvent.RestartApp -> restartApp()
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    /** 使用独立启动 Intent 重开进程，避免已关闭的 Room 和缓存偏好继续参与业务。 */
    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            73041,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 700,
            pendingIntent
        )
        finishAffinity()
        Process.killProcess(Process.myPid())
    }
}
