package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyDirectory
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.copyFile
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.extractArchive
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.sha256
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.validateChecksums
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.writeJsonLine
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFiles.writeText
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.BUFFER_SIZE
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.FORMAT
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.LEGACY_VERSION
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.LOGS_DB
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.PRIMARY_DB
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.TABLE_ROWS_KEY
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.VERSION
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.collectTableRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.readLegacyRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.readRows
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.reconstructDatabase
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.schemaJson
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.tableNames
import me.kafuuneko.rpclient.libs.backup.BackupDatabaseCodec.tableRows
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 按功能组织可移植备份，在 JSON/JSONL 中保存完整 Room 数据行，
 * 使关系与文件引用可以重建，而无需依赖原始 SQLite 文件。
 */
class PortableBackupRepository(
    private val mContext: Context,
    private val mDatabase: AppDatabase,
    private val mRequestLogDatabase: RequestLogDatabase,
    private val mLegacyRepository: BackupArchiveRepository
) {

    /**
     * 导出当前业务数据、设置和附件；完整 ZIP 按明文协议保存凭据。
     * @param destination 用户通过系统选择器创建的 ZIP 文档。
     */
    suspend fun exportArchive(destination: Uri) = withContext(Dispatchers.IO) {
        val stage = File(mContext.cacheDir, "portable-export-" + System.nanoTime())
        check(stage.mkdirs()) { "Could not create temporary backup workspace." }
        try {
            // 业务行与附件在同一主库快照中导出，避免引用与物理文件错位。
            createArchiveDirectories(stage)
            val primaryDb = mDatabase.openHelper.writableDatabase
            val primaryTables = tableNames(primaryDb)
            primaryDb.beginTransaction()
            try {
                writeText(
                    File(stage, "app-data/database-schema.json"),
                    schemaJson(primaryDb).toString(2)
                )
                PortableBackupDocumentWriter(
                    mDatabase = primaryDb,
                    mTables = primaryTables,
                    mRoot = stage
                ).writeAll()
                exportAttachments(primaryDb, primaryTables, stage)
                primaryDb.setTransactionSuccessful()
            } finally {
                primaryDb.endTransaction()
            }

            val logsDb = mRequestLogDatabase.openHelper.writableDatabase
            logsDb.beginTransaction()
            try {
                writeText(
                    File(stage, "app-data/request-log-schema.json"),
                    schemaJson(logsDb).toString(2)
                )
                exportRequestLogs(logsDb, stage)
                logsDb.setTransactionSuccessful()
            } finally {
                logsDb.endTransaction()
            }

            // 设置和私有文件保持原始格式，manifest 在全部内容写完后生成。
            exportSettings(stage)
            copyDirectory(mContext.filesDir, File(stage, "app-data/private-files"))

            writeManifest(stage)

            writeArchive(stage, destination)
        } finally {
            stage.deleteRecursively()
        }
    }

    /** 在所有暂存文件完成后生成大小与 SHA-256 索引。 */
    private fun writeManifest(stage: File) {
        val fileIndex = JSONObject()
        stage.walkTopDown().filter { it.isFile }.toList().forEach { file ->
            fileIndex.put(
                file.relativeTo(stage).invariantSeparatorsPath,
                JSONObject()
                    .put("size", file.length()).put("sha256", sha256(file)),
            )
        }
        // manifest 不索引自身，避免摘要形成自引用。
        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("appPackage", mContext.packageName)
            .put("encrypted", false)
            .put("restoreMode", "full-replacement")
            .put(
                "structure",
                JSONArray()
                    .put("characters/character-<id>.json")
                    .put("stories/story-<id>.rpstory.json")
                    .put("chats/conversation-<id>.jsonl")
                    .put("group-chats/group-<id>.jsonl")
                    .put("worldbooks/worldbook-<id>.json")
                    .put("regex/global-scripts.json")
                    .put("regex/character-scripts/character-<id>.json")
                    .put("regex/character-authorizations.json")
                    .put("providers/provider-configurations.json")
                    .put("attachments/characters/")
                    .put("attachments/chat-images/")
                    .put("attachments/other-files/")
                    .put("app-data/token-usage.json"),
            )
            .put(
                "components",
                JSONObject()
                    .put("characters", true)
                    .put("stories", true)
                    .put("chats", true)
                    .put("groupChats", true)
                    .put("worldbooks", true)
                    .put("regex", true)
                    .put("providersAndApiKeys", true)
                    .put("settingsAndPrompts", true)
                    .put("tokenUsage", true)
                    .put("requestLogs", true)
                    .put("attachments", true)
                    .put("privateAppFiles", true),
            )
            .put("recordCounts", countRecords(stage))
            .put("files", fileIndex)
            .put(
                "notes",
                "Feature-oriented JSON/JSONL with exact relational rows. Not encrypted; may contain API keys and raw request/response logs.",
            )
        writeText(File(stage, "manifest.json"), manifest.toString(2))
    }

    /** 逐文件写入 ZIP，避免一次性将整个归档加载到堆内存。 */
    private fun writeArchive(stage: File, destination: Uri) {
        val output = mContext.contentResolver.openOutputStream(destination, "wt")
            ?: error("Could not open the selected destination.")
        output.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                // 保留空目录条目，恢复时可区分空目录与归档缺项。
                stage.walkTopDown().drop(1).forEach { file ->
                    val path = file.relativeTo(stage).invariantSeparatorsPath
                    if (file.isDirectory) {
                        zip.putNextEntry(ZipEntry(path.trimEnd('/') + "/"))
                        zip.closeEntry()
                    } else {
                        zip.putNextEntry(ZipEntry(path))
                        FileInputStream(file).use { fileInputStream ->
                            fileInputStream.copyTo(
                                zip,
                                BUFFER_SIZE
                            )
                        }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    /**
     * 校验并恢复可移植或兼容旧版归档；成功后调用方必须重启应用。
     * @param source 用户确认恢复的 ZIP 文档。
     * @throws BackupRestoreException 数据库关闭后的失败，携带重启和回滚状态。
     */
    suspend fun restoreArchive(source: Uri) = withContext(Dispatchers.IO) {
        val stage = File(mContext.cacheDir, "portable-restore-" + System.nanoTime())
        val rollback = File(mContext.cacheDir, "portable-rollback-" + System.nanoTime())
        check(stage.mkdirs()) { "Could not create temporary restore workspace." }
        try {
            extractArchive(mContext, source, stage)
            val manifestFile = File(stage, "manifest.json")
            require(manifestFile.isFile) { "This ZIP is missing manifest.json." }
            val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
            // 格式识别后交给对应解码器，旧格式沿用既有恢复范围。
            if (manifest.optString("format") == "rpclient-backup") {
                mLegacyRepository.restoreArchive(source)
                return@withContext
            }
            require(manifest.optString("format") == FORMAT) { "This is not an RPClient portable data backup." }
            val version = manifest.optInt("version", -1)
            require(version == VERSION || version == LEGACY_VERSION) {
                "Unsupported portable backup version: " + version
            }
            validateChecksums(
                root = stage,
                files = manifest.optJSONObject("files")
                    ?: error("The manifest does not list archive files."),
            )

            if (version == LEGACY_VERSION && File(stage, "database/primary/schema.json").isFile) {
                val legacyPrimary = File(stage, "database/primary")
                val legacyLogs = File(stage, "database/request-logs")
                val stagedPrimary = File(stage, PRIMARY_DB)
                val stagedLogs = File(stage, LOGS_DB)
                reconstructDatabase(
                    schema = JSONObject(
                        File(
                            legacyPrimary,
                            "schema.json"
                        ).readText(Charsets.UTF_8)
                    ),
                    tableRows = readLegacyRows(legacyPrimary),
                    destination = stagedPrimary,
                )
                reconstructDatabase(
                    schema = JSONObject(File(legacyLogs, "schema.json").readText(Charsets.UTF_8)),
                    tableRows = readLegacyRows(legacyLogs),
                    destination = stagedLogs,
                )
                replaceLiveData(
                    rollback = rollback,
                    stagedPrimary = stagedPrimary,
                    stagedLogs = stagedLogs,
                    stagedPrefs = File(stage, "settings/shared_prefs"),
                    stagedRepository = File(stage, "attachments/repository"),
                    stagedFiles = File(stage, "app-files"),
                )
                return@withContext
            }

            require(version == VERSION) { "Legacy archive layout is incomplete." }
            val primarySchemaFile = File(stage, "app-data/database-schema.json")
            val logsSchemaFile = File(stage, "app-data/request-log-schema.json")
            require(primarySchemaFile.isFile) { "The primary database schema is missing." }
            require(logsSchemaFile.isFile) { "The request-log database schema is missing." }
            val primaryRows =
                collectTableRows(root = stage, excludePath = "app-data/request-logs.jsonl")
            val requestRows =
                collectTableRows(root = stage, includePath = "app-data/request-logs.jsonl")
            val stagedPrimary = File(stage, PRIMARY_DB)
            val stagedLogs = File(stage, LOGS_DB)
            reconstructDatabase(
                schema = JSONObject(primarySchemaFile.readText(Charsets.UTF_8)),
                tableRows = primaryRows,
                destination = stagedPrimary,
            )
            reconstructDatabase(
                schema = JSONObject(logsSchemaFile.readText(Charsets.UTF_8)),
                tableRows = requestRows,
                destination = stagedLogs,
            )

            replaceLiveData(
                rollback = rollback,
                stagedPrimary = stagedPrimary,
                stagedLogs = stagedLogs,
                stagedPrefs = File(stage, "app-data/shared-prefs"),
                stagedRepository = File(stage, "attachments/other-files"),
                stagedFiles = File(stage, "app-data/private-files"),
            )
        } finally {
            stage.deleteRecursively()
        }
    }

    /** 显式写入必要空目录，使空设置或空附件也能完整恢复。 */
    private fun createArchiveDirectories(root: File) {
        listOf(
            "characters",
            "stories",
            "chats",
            "group-chats",
            "worldbooks",
            "regex/character-scripts",
            "providers",
            "attachments/characters",
            "attachments/chat-images",
            "attachments/other-files",
            "app-data/shared-prefs",
            "app-data/private-files",
        ).forEach { File(root, it).mkdirs() }
    }

    /** 同时保存可读偏好和原始 XML，恢复使用原始文件以保留类型。 */
    private fun exportSettings(root: File) {
        val sourcePrefs = File(mContext.dataDir, "shared_prefs")
        val targetPrefs = File(root, "app-data/shared-prefs")
        copyDirectory(sourcePrefs, targetPrefs)
        // JSON 便于查看；XML 作为精确恢复的权威数据。
        val preferences = JSONObject()
        targetPrefs.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".xml") }
            .forEach { xml ->
                val prefsName = xml.name.removeSuffix(".xml")
                preferences.put(
                    prefsName,
                    sharedPreferencesJson(
                        preferences = mContext.getSharedPreferences(
                            prefsName,
                            Context.MODE_PRIVATE
                        )
                    ),
                )
            }
        writeText(
            file = File(root, "settings.json"),
            content = JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("preferences", preferences)
                .put("providerCredentialsIncluded", true)
                .put("encrypted", false)
                .put("rawPreferenceXml", "app-data/shared-prefs/")
                .put(
                    "note",
                    "Preferences are provided as readable JSON and preserved as original XML for exact restoration.",
                )
                .toString(2),
        )
    }

    /** 将 Android 偏好类型转为便于人工读取的 JSON。 */
    private fun sharedPreferencesJson(preferences: SharedPreferences): JSONObject {
        val json = JSONObject()
        // 字符串集合排序只影响可读输出，不改变实际偏好类型。
        preferences.all.toSortedMap().forEach { (key, value) ->
            when (value) {
                null -> json.put(key, JSONObject.NULL)
                is String -> json.put(key, value)
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value.toDouble())
                is Set<*> -> json.put(
                    key,
                    JSONArray().apply {
                        value.filterIsInstance<String>().sorted().forEach { text -> put(text) }
                    },
                )

                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    /** 保存仓库的标准附件，并为角色和消息生成易于识别的分类副本。 */
    private fun exportAttachments(db: SupportSQLiteDatabase, tables: List<String>, root: File) {
        val fileRows = if ("files" in tables) readRows(db, "files").objects() else emptyList()
        val fileByUuid = fileRows.associateBy { it.optString("uuid") }
        val repository = mContext.getDir("repository", Context.MODE_PRIVATE)
        val otherFiles = File(root, "attachments/other-files")
        otherFiles.mkdirs()
        // 文件索引里的引用必须存在，缺失附件不能导出为看似完整的备份。
        fileRows.forEach { row ->
            val hash = row.optString("hash")
            require(hash.matches(Regex("[0-9a-fA-F]{64}"))) {
                "Invalid stored attachment hash for " + row.optString("uuid")
            }
            val source = File(repository, hash)
            require(source.isFile) { "An attachment is missing from storage: " + hash }
            copyFile(source, File(otherFiles, hash))
        }
        repository.listFiles().orEmpty().filter { file ->
            file.isFile && file.name.matches(Regex("[0-9a-fA-F]{64}"))
        }.forEach { copyFile(it, File(otherFiles, it.name)) }

        if ("character" in tables) {
            readRows(db, "character").objects().forEach { character ->
                val fileRecord = fileByUuid[character.optString("avatar")] ?: return@forEach
                val hash = fileRecord.optString("hash")
                val asset = File(repository, hash)
                if (asset.isFile) copyFile(
                    source = asset,
                    destination = File(
                        root,
                        "attachments/characters/character-" + character.optLong("id") + "-avatar-" + hash,
                    ),
                )
            }
        }
        if ("message_images" in tables) {
            readRows(db, "message_images").objects().forEach { image ->
                val fileRecord = fileByUuid[image.optString("imageUuid")] ?: return@forEach
                val hash = fileRecord.optString("hash")
                val asset = File(repository, hash)
                if (asset.isFile) {
                    val type = image.optString("messageType")
                    val messageId = image.optLong("messageId")
                    val position = image.optInt("position")
                    copyFile(
                        source = asset,
                        destination = File(
                            root,
                            "attachments/chat-images/" + type + "-" + messageId + "-" + position + "-" + hash,
                        ),
                    )
                }
            }
        }
    }

    /** 按行输出请求日志及日志库的扩展表。 */
    private fun exportRequestLogs(db: SupportSQLiteDatabase, root: File) {
        File(root, "app-data/request-logs.jsonl").bufferedWriter(Charsets.UTF_8).use { writer ->
            tableNames(db).forEach { table ->
                readRows(db, table).objects().forEach { row ->
                    val isRequestLog = table == "llm_request_logs"
                    writeJsonLine(
                        json = JSONObject()
                            .put("type", if (isRequestLog) "request-log" else "internal-table")
                            .put("table", table)
                            .put("record", row)
                            .put(TABLE_ROWS_KEY, tableRows(table to JSONArray().put(row))),
                        writer = writer,
                    )
                }
            }
        }
    }

    /** 校验两个暂存库，再通过统一替换器保存快照并覆盖全部数据。 */
    private fun replaceLiveData(
        rollback: File,
        stagedPrimary: File,
        stagedLogs: File,
        stagedPrefs: File,
        stagedRepository: File,
        stagedFiles: File,
    ) {
        require(stagedPrefs.isDirectory) { "Backup preference files are missing." }
        require(stagedRepository.isDirectory) { "Backup attachments are missing." }
        require(stagedFiles.isDirectory) { "Backup app-private files are missing." }
        // 先在暂存库执行正常迁移；不兼容的新版本不能进入覆盖流程。
        BackupDatabaseValidator.validatePrimary(mContext, stagedPrimary)
        BackupDatabaseValidator.validateLogs(mContext, stagedLogs)
        BackupDataReplacer.replace(
            rollback = rollback,
            replacements = listOf(
                BackupReplacement(
                    source = stagedPrimary,
                    target = mContext.getDatabasePath(PRIMARY_DB),
                    isDatabase = true,
                ),
                BackupReplacement(
                    source = stagedLogs,
                    target = mContext.getDatabasePath(LOGS_DB),
                    isDatabase = true
                ),
                BackupReplacement(stagedPrefs, File(mContext.dataDir, "shared_prefs")),
                BackupReplacement(
                    stagedRepository,
                    mContext.getDir("repository", Context.MODE_PRIVATE)
                ),
                BackupReplacement(stagedFiles, mContext.filesDir),
            ),
        ) {
            mDatabase.close()
            mRequestLogDatabase.close()
        }
    }

    /** 统计功能文档数量，供 manifest 展示归档内容规模。 */
    private fun countRecords(root: File): JSONObject {
        val counts = JSONObject()
        val folders = listOf(
            "characters" to "characters",
            "stories" to "stories",
            "chats" to "chats",
            "groupChats" to "group-chats",
            "worldbooks" to "worldbooks",
            "providers" to "providers",
        )
        folders.forEach { (key, path) ->
            counts.put(key, File(root, path).walkTopDown().count { file -> file.isFile })
        }
        // 请求日志文件也携带内部表，统计时只计业务日志条目。
        var requestLogs = 0
        File(root, "app-data/request-logs.jsonl").takeIf { it.isFile }
            ?.forEachLine(Charsets.UTF_8) { line ->
                if (line.isNotBlank() && runCatching { JSONObject(line).optString("type") == "request-log" }.getOrDefault(
                        defaultValue = false
                    )
                ) {
                    requestLogs++
                }
            }
        counts.put("requestLogs", requestLogs)
        return counts
    }

}
