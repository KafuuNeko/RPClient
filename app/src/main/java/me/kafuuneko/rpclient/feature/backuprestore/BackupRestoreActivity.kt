package me.kafuuneko.rpclient.feature.backuprestore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.MainActivity
import me.kafuuneko.rpclient.libs.backup.BackupRestoreException
import me.kafuuneko.rpclient.libs.backup.PortableBackupRepository
import me.kafuuneko.rpclient.libs.core.CoreActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Hosts portable full-data ZIP backup and full replacement restore. */
class BackupRestoreActivity : CoreActivity(), KoinComponent {
    private val backupRepository by inject<PortableBackupRepository>()

    @Composable
    override fun ViewContent() {
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<String?>(null) }
        var restoreUri by remember { mutableStateOf<Uri?>(null) }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null) {
                busy = true
                status = getString(R.string.backup_working)
                lifecycleScope.launch {
                    try {
                        backupRepository.exportArchive(uri)
                        status = getString(R.string.backup_export_success)
                    } catch (error: Throwable) {
                        status = getString(R.string.backup_operation_failed, error.message ?: error.javaClass.simpleName)
                    } finally {
                        busy = false
                    }
                }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            restoreUri = it
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(getString(R.string.backup_restore_title), style = MaterialTheme.typography.headlineSmall)
                Text(getString(R.string.backup_restore_description), style = MaterialTheme.typography.bodyMedium)
                Text(
                    getString(R.string.backup_restore_security_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { exportLauncher.launch("RPClient-Portable-Data.zip") }
                ) { Text(getString(R.string.backup_export_action)) }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                ) { Text(getString(R.string.backup_restore_action)) }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.weight(1f))
                TextButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { finish() }) {
                    Text(getString(R.string.backup_done))
                }
            }
        }

        if (restoreUri != null) {
            AlertDialog(
                onDismissRequest = { if (!busy) restoreUri = null },
                title = { Text(getString(R.string.backup_restore_confirm_title)) },
                text = { Text(getString(R.string.backup_restore_confirm_message)) },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        val selectedUri = restoreUri ?: return@TextButton
                        restoreUri = null
                        busy = true
                        status = getString(R.string.backup_working)
                        lifecycleScope.launch {
                            try {
                                backupRepository.restoreArchive(selectedUri)
                                status = getString(R.string.backup_restore_success)
                                restartApp()
                            } catch (error: Throwable) {
                                val restoreError = error as? BackupRestoreException
                                status = getString(
                                    R.string.backup_operation_failed,
                                    error.message ?: error.javaClass.simpleName
                                )
                                busy = false
                                if (restoreError?.restartRequired == true) {
                                    Toast.makeText(this@BackupRestoreActivity, R.string.backup_restarting, Toast.LENGTH_LONG).show()
                                    restartApp()
                                }
                            }
                        }
                    }) { Text(getString(R.string.backup_restore_action)) }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = { restoreUri = null }) { Text(getString(R.string.cancel)) }
                }
            )
        }
    }

    private fun restartApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this, 73041, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 700, pendingIntent)
        finishAffinity()
        Process.killProcess(Process.myPid())
    }
}
