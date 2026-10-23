package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.extractArchive
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.sha256
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.json.JSONObject
import java.io.File

/**
 * 旧版 SQLite 快照的只读兼容入口。
 * - 导出统一使用 PortableBackupRepository，不再维护重复的旧格式写入逻辑。
 * - 完成结构校验后复用可移植格式的回滚替换流程。
 */
class BackupArchiveRepository(
    private val mContext: Context,
    private val mDatabase: AppDatabase
) {
    companion object {
        private const val FORMAT = "rpclient-backup"
        private const val VERSION = 1
        private const val DATABASE_NAME = "primary.sqlite"
    }

    /** 先校验并暂存归档，再替换当前文件；替换失败时使用回滚快照。 */
    suspend fun restoreArchive(source: Uri) = withContext(Dispatchers.IO) {
        val stageRoot = File(mContext.cacheDir, "backup-restore-" + System.nanoTime())
        val rollbackRoot = File(mContext.cacheDir, "backup-rollback-" + System.nanoTime())
        stageRoot.mkdirs()
        try {
            // 旧格式复用路径穿越、重复条目和解压大小校验。
            extractArchive(mContext, source, stageRoot)
            val manifestFile = File(stageRoot, "manifest.json")
            require(manifestFile.isFile) { "This ZIP is missing manifest.json." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(manifest.optString("format") == FORMAT) { "This is not an RPClient backup." }
            require(manifest.optInt("version", -1) == VERSION) {
                "Unsupported backup format version: " + manifest.optInt("version", -1)
            }

            val stagedDatabase = File(stageRoot, "database/$DATABASE_NAME")
            require(stagedDatabase.isFile) { "The archive is missing database/$DATABASE_NAME." }
            require(
                value = stagedDatabase.inputStream().use { inputStream ->
                    val header = ByteArray(16)
                    inputStream.read(header) == 16
                        && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
                },
            ) { "The backup database is not a valid SQLite database." }
            require(
                sha256(stagedDatabase).equals(
                    other = manifest.optString("databaseSha256"),
                    ignoreCase = true
                )
            ) {
                "The database checksum does not match manifest.json."
            }

            BackupDatabaseValidator.validatePrimary(mContext, stagedDatabase)
            val stagedPrefs = File(stageRoot, "settings/shared_prefs")
            val stagedRepository = File(stageRoot, "files/repository")
            // 旧导出器不写空目录条目；缺省目录按旧协议恢复为空目录。
            stagedPrefs.mkdirs()
            stagedRepository.mkdirs()
            BackupDataReplacer.replace(
                rollback = rollbackRoot,
                replacements = listOf(
                    BackupReplacement(
                        source = stagedDatabase,
                        target = mContext.getDatabasePath(DATABASE_NAME),
                        isDatabase = true,
                    ),
                    BackupReplacement(stagedPrefs, File(mContext.dataDir, "shared_prefs")),
                    BackupReplacement(
                        stagedRepository,
                        mContext.getDir("repository", Context.MODE_PRIVATE)
                    ),
                ),
            ) { mDatabase.close() }
        } finally {
            stageRoot.deleteRecursively()
        }
    }

}

/** 数据库连接已关闭后的恢复错误，调用方必须按标志重启以重建连接。 */
class BackupRestoreException(
    message: String,
    val restartRequired: Boolean,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
