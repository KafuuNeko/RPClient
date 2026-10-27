package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyDirectory
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyFile
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.extractArchive
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.validateChecksums
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.writeText
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.BUFFER_SIZE
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.FORMAT
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.LEGACY_VERSION
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.LOGS_DB
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.PRIMARY_DB
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.VERSION
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.VERSION_3
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.collectTableRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.readLegacyRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.reconstructDatabase
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.schemaJson
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.tableNames
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import org.json.JSONObject

/**
 * 按功能组织可移植备份，在 JSON/JSONL 中保存完整 Room 数据行，
 * 使关系与文件引用可以重建，而无需依赖原始 SQLite 文件。
 */
class PortableBackupRepository(
    private val mContext: Context,
    private val mDatabase: AppDatabase,
    private val mRequestLogDatabase: RequestLogDatabase,
    private val mLegacyRepository: BackupArchiveRepository,
    private val mGson: Gson = Gson()
) {

    /**
     * 导出当前业务数据、设置和附件；完整 ZIP 按明文协议保存凭据。
     * @param destination 用户通过系统选择器创建的 ZIP 文档。
     * @param report 接收阶段日志与警告，供页面展示技术详情。
     */
    suspend fun exportArchive(destination: Uri, report: BackupReport = BackupReport()) =
        withContext(Dispatchers.IO) {
            report.info("Backup started (portable format v$VERSION_3)")
            val stage = File(mContext.cacheDir, "portable-export-" + System.nanoTime())
            val tempZip = File(mContext.cacheDir, "portable-export-" + System.nanoTime() + ".zip")
            try {
                check(stage.mkdirs()) { "Could not create temporary backup workspace." }
                // 业务行与附件在同一主库快照中导出，避免引用与物理文件错位。
                val primaryDb = mDatabase.openHelper.writableDatabase
                val primaryTables = tableNames(primaryDb)
                primaryDb.beginTransaction()
                try {
                    writeText(File(stage, "app-data/database-schema.json"), schemaJson(primaryDb).toString(2))
                    BackupV3Writer(
                        primaryDb, primaryTables, stage,
                        mContext.getDir("repository", Context.MODE_PRIVATE), mGson, report
                    ).writeAll()
                    exportAttachments(primaryDb, primaryTables, stage, report)
                    primaryDb.setTransactionSuccessful()
                } finally {
                    primaryDb.endTransaction()
                }

                val logsDb = mRequestLogDatabase.openHelper.writableDatabase
                logsDb.beginTransaction()
                try {
                    writeText(File(stage, "app-data/request-log-schema.json"), schemaJson(logsDb).toString(2))
                    tableNames(logsDb).forEach { table ->
                        val count = BackupV3.dumpTable(logsDb, table, File(stage, "request-logs/$table.jsonl"))
                        if (count > 0) report.info("request log table $table: $count rows")
                    }
                    logsDb.setTransactionSuccessful()
                } finally {
                    logsDb.endTransaction()
                }

                // 偏好保持原始 XML（保留类型），私有文件保持原样；它们没有菜单级导入格式。
                copyDirectory(File(mContext.dataDir, "shared_prefs"), File(stage, "shared-prefs"))
                copyDirectory(mContext.filesDir, File(stage, "private-files"))
                report.info("preferences and private files copied")

                writeManifest(stage)
                report.info("Packing ZIP")
                writeArchive(stage, tempZip)
                copyToDestination(tempZip, destination)
                report.info("Backup finished")
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (error is BackupOperationException) throw error
                throw BackupOperationException(report.statusMessage(error, "Backup"), error)
            } finally {
                stage.deleteRecursively()
                tempZip.delete()
            }
        }

    /** v3 清单只描述格式，不再记录校验和：用户可以直接编辑、删除或新增归档内的文件。 */
    private fun writeManifest(stage: File) {
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION_3)
            .put("createdAt", System.currentTimeMillis())
            .put("appPackage", mContext.packageName)
            .put("encrypted", false)
            .put("restoreMode", "full-replacement")
            .put(
                "notes",
                "Edit files in place: characters/ (Character Card V2), worldbooks/ (SillyTavern lorebook), " +
                    "regex/, stories/ (.rpstory.json), chats/ (SillyTavern JSONL). Each file can also be " +
                    "imported by the matching menu. The rpclient_backup key keeps local database ids. " +
                    "Not encrypted; may contain API keys and raw request/response logs."
            )
        writeText(File(stage, "manifest.json"), manifest.toString(2))
    }

    /** 逐文件写入 ZIP，避免一次性将整个归档加载到堆内存。 */
    private fun writeArchive(stage: File, target: File) {
        target.outputStream().use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                stage.walkTopDown().drop(1).forEach { file ->
                    val path = file.relativeTo(stage).invariantSeparatorsPath
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry(path.trimEnd('/') + "/"))
                        zip.closeEntry()
                    } else {
                        zip.putNextEntry(ZipEntry(path))
                        FileInputStream(file).use { it.copyTo(zip, BUFFER_SIZE) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /** 先完整生成临时 ZIP 再写入目标，失败时不会留下被截断的用户文件。 */
    private fun copyToDestination(source: File, destination: Uri) {
        val output = mContext.contentResolver.openOutputStream(destination, "wt")
            ?: error("Could not open the selected destination.")
        output.use { out -> FileInputStream(source).use { it.copyTo(out, BUFFER_SIZE) } }
    }

    /**
     * 校验并恢复可移植或兼容旧版归档；成功后调用方必须重启应用。
     * @param source 用户确认恢复的 ZIP 文档。
     * @throws BackupRestoreException 数据库关闭后的失败，携带重启和回滚状态。
     */
    suspend fun restoreArchive(source: Uri, report: BackupReport = BackupReport()) = withContext(Dispatchers.IO) {
        val stage = File(mContext.cacheDir, "portable-restore-" + System.nanoTime())
        val rollback = File(mContext.cacheDir, "portable-rollback-" + System.nanoTime())
        check(stage.mkdirs()) { "Could not create temporary restore workspace." }
        try {
            report.info("Extracting ZIP")
            extractArchive(mContext, source, stage)
            val manifestFile = File(stage, "manifest.json")
            require(manifestFile.isFile) { "This ZIP is missing manifest.json." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            // 格式识别后交给对应解码器，旧格式沿用既有恢复范围。
            if (manifest.optString("format") == "rpclient-backup") {
                report.info("Format: rpclient-backup (legacy)")
                mLegacyRepository.restoreArchive(source)
                return@withContext
            }
            require(manifest.optString("format") == FORMAT) { "This is not an RPClient portable data backup." }
            val version = manifest.optInt("version", -1)
            report.info("Format: $FORMAT, version $version")
            if (version == VERSION_3) {
                restoreV3(stage, rollback, report)
                return@withContext
            }
            require(version == VERSION || version == LEGACY_VERSION) {
                "Unsupported portable backup version: " + version
            }
            validateChecksums(stage, manifest.optJSONObject("files")
                ?: error("The manifest does not list archive files."))

            if (version == LEGACY_VERSION && File(stage, "database/primary/schema.json").isFile) {
                val legacyPrimary = File(stage, "database/primary")
                val legacyLogs = File(stage, "database/request-logs")
                val stagedPrimary = File(stage, PRIMARY_DB)
                val stagedLogs = File(stage, LOGS_DB)
                reconstructDatabase(
                    JSONObject(File(legacyPrimary, "schema.json").readText(Charsets.UTF_8)),
                    readLegacyRows(legacyPrimary), stagedPrimary
                )
                reconstructDatabase(
                    JSONObject(File(legacyLogs, "schema.json").readText(Charsets.UTF_8)),
                    readLegacyRows(legacyLogs), stagedLogs
                )
                replaceLiveData(
                    rollback, stagedPrimary, stagedLogs,
                    File(stage, "settings/shared_prefs"),
                    File(stage, "attachments/repository"),
                    File(stage, "app-files")
                )
                return@withContext
            }

            require(version == VERSION) { "Legacy archive layout is incomplete." }
            val primarySchemaFile = File(stage, "app-data/database-schema.json")
            val logsSchemaFile = File(stage, "app-data/request-log-schema.json")
            require(primarySchemaFile.isFile) { "The primary database schema is missing." }
            require(logsSchemaFile.isFile) { "The request-log database schema is missing." }
            val primaryRows = collectTableRows(stage, excludePath = "app-data/request-logs.jsonl")
            val requestRows = collectTableRows(stage, includePath = "app-data/request-logs.jsonl")
            val stagedPrimary = File(stage, PRIMARY_DB)
            val stagedLogs = File(stage, LOGS_DB)
            reconstructDatabase(
                JSONObject(primarySchemaFile.readText(Charsets.UTF_8)), primaryRows, stagedPrimary
            )
            reconstructDatabase(
                JSONObject(logsSchemaFile.readText(Charsets.UTF_8)), requestRows, stagedLogs
            )

            replaceLiveData(
                rollback, stagedPrimary, stagedLogs,
                File(stage, "app-data/shared-prefs"),
                File(stage, "attachments/other-files"),
                File(stage, "app-data/private-files")
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    /** v3 恢复：文件内容是唯一事实来源，读取后重建数据库并移除缺少父记录的行。 */
    private fun restoreV3(stage: File, rollback: File, report: BackupReport) {
        val primarySchemaFile = File(stage, "app-data/database-schema.json")
        val logsSchemaFile = File(stage, "app-data/request-log-schema.json")
        require(primarySchemaFile.isFile) { "app-data/database-schema.json is missing." }
        require(logsSchemaFile.isFile) { "app-data/request-log-schema.json is missing." }
        val reader = BackupV3Reader(stage, mGson, report)
        val primaryRows = reader.readPrimary()
        val requestRows = reader.readLogs()
        val stagedPrimary = File(stage, PRIMARY_DB)
        val stagedLogs = File(stage, LOGS_DB)
        report.info("Rebuilding databases")
        reconstructDatabase(
            JSONObject(primarySchemaFile.readText(Charsets.UTF_8)), primaryRows, stagedPrimary
        ) { report.warn(it) }
        reconstructDatabase(
            JSONObject(logsSchemaFile.readText(Charsets.UTF_8)), requestRows, stagedLogs
        ) { report.warn(it) }
        // 用户可能删光某个目录；空目录在 ZIP 中不一定保留。
        val prefs = File(stage, "shared-prefs").apply { mkdirs() }
        val attachments = File(stage, "attachments").apply { mkdirs() }
        val privateFiles = File(stage, "private-files").apply { mkdirs() }
        report.info("Validating databases and replacing current data")
        replaceLiveData(rollback, stagedPrimary, stagedLogs, prefs, attachments, privateFiles)
        report.info("Data replaced; restart required")
    }

    /** 附件按哈希保存一份；文件索引引用的附件缺失时只警告，不让整个备份失败。 */
    private fun exportAttachments(
        db: SupportSQLiteDatabase,
        tables: List<String>,
        root: File,
        report: BackupReport
    ) {
        val repository = mContext.getDir("repository", Context.MODE_PRIVATE)
        val target = File(root, "attachments").apply { mkdirs() }
        val hashPattern = Regex("[0-9a-fA-F]{64}")
        var copied = 0
        repository.listFiles().orEmpty().filter { it.isFile && it.name.matches(hashPattern) }.forEach {
            copyFile(it, File(target, it.name))
            copied += 1
        }
        if ("files" in tables) {
            BackupDatabaseCodec.forEachRow(db, "files") { row ->
                val hash = row.optString("hash")
                if (!hash.matches(hashPattern)) {
                    report.warn("files row " + row.optString("uuid") + " has an invalid hash")
                } else if (!File(repository, hash).isFile) {
                    report.warn("files row " + row.optString("uuid") + " references a missing attachment " + hash)
                }
            }
        }
        report.info("attachments: $copied")
    }

    /** 校验两个暂存库，再通过统一替换器保存快照并覆盖全部数据。 */
    private fun replaceLiveData(
        rollback: File,
        stagedPrimary: File,
        stagedLogs: File,
        stagedPrefs: File,
        stagedRepository: File,
        stagedFiles: File
    ) {
        require(stagedPrefs.isDirectory) { "Backup preference files are missing." }
        require(stagedRepository.isDirectory) { "Backup attachments are missing." }
        require(stagedFiles.isDirectory) { "Backup app-private files are missing." }
        // 先在暂存库执行正常迁移；不兼容的新版本不能进入覆盖流程。
        BackupDatabaseValidator.validatePrimary(mContext, stagedPrimary)
        BackupDatabaseValidator.validateLogs(mContext, stagedLogs)
        BackupDataReplacer.replace(rollback, listOf(
            BackupReplacement(stagedPrimary, mContext.getDatabasePath(PRIMARY_DB), isDatabase = true),
            BackupReplacement(stagedLogs, mContext.getDatabasePath(LOGS_DB), isDatabase = true),
            BackupReplacement(stagedPrefs, File(mContext.dataDir, "shared_prefs")),
            BackupReplacement(stagedRepository, mContext.getDir("repository", Context.MODE_PRIVATE)),
            BackupReplacement(stagedFiles, mContext.filesDir)
        )) {
            mDatabase.close()
            mRequestLogDatabase.close()
        }
    }
}
