package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** 使用隔离目录和真实 SQLite 验证备份与 Develop 搜索、背景资源的兼容性。 */
@RunWith(AndroidJUnit4::class)
class PortableBackupRepositoryTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var primary: AppDatabase
    private lateinit var logs: RequestLogDatabase

    /** 所有数据库和文件均位于本项测试的临时目录。 */
    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(base.cacheDir, "portable-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getDataDir(): File = root
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
            override fun getDir(name: String, mode: Int): File =
                File(root, "app_$name").apply { mkdirs() }

            override fun getDatabasePath(name: String): File =
                (if (File(name).isAbsolute) File(name) else File(
                    root,
                    "databases/$name"
                )).also { file ->
                    file.parentFile?.mkdirs()
                }
        }
        File(root, "shared_prefs").mkdirs()
        openDatabases()
    }

    /** 关闭测试库后只清理测试创建的目录。 */
    @After
    fun tearDown() {
        primary.close()
        logs.close()
        root.deleteRecursively()
    }

    /** 原始行、FTS rowid、开关、背景资源和同名私有文件均应无损往返。 */
    @Test
    fun roundTripPreservesSearchBackgroundAndPrivateFiles() = runBlocking {
        val db = primary.openHelper.writableDatabase
        db.execSQL(sql = "INSERT INTO search_chunks(rowid, kind, sourceId, parentId, revision, position, tokens) VALUES(42, 1, 7, 3, 1, 0, 'hello world')")
        db.execSQL("INSERT OR REPLACE INTO search_progress(kind, cursor, complete) VALUES(-2, 2, 1)")
        db.execSQL(
            sql = "INSERT INTO search_progress(kind, cursor, complete) VALUES(-1, ?, 1)",
            bindArgs = arrayOf(SearchTextCodec.FORMAT_VERSION),
        )
        for (kind in 1..3) db.execSQL(
            sql = "INSERT INTO search_progress(kind, cursor, complete) VALUES(?, 7, 1)",
            bindArgs = arrayOf(kind),
        )
        db.execSQL("CREATE TABLE backup_probe(id INTEGER PRIMARY KEY, payload BLOB, note TEXT)")
        db.execSQL("INSERT INTO backup_probe VALUES(9, X'010203', NULL)")
        val logDb = logs.openHelper.writableDatabase
        logDb.execSQL("CREATE TABLE backup_log_probe(value TEXT)")
        logDb.execSQL("INSERT INTO backup_log_probe VALUES('test log')")
        // 背景文件通过实际仓库提交，验证新增关系表和附件索引不会丢失。
        val files = FileRepository(context, primary)
        val prepared = files.prepareStream(
            "backup-test",
            ByteArrayInputStream("background".toByteArray()),
            "image/png"
        )
        PageBackgroundRepository(primary, files).save(
            PageBackground(
                page = BackgroundPage.Story,
                blur = 37
            ), prepared
        )
        File(context.filesDir, "manifest.json").writeText("private file")
        val archive = File(root, "snapshot.zip")
        PortableBackupRepository(
            mContext = context,
            mDatabase = primary,
            mRequestLogDatabase = logs,
            mLegacyRepository = BackupArchiveRepository(
                mContext = context,
                mDatabase = primary,
            ),
        ).exportArchive(Uri.fromFile(archive))
        ZipFile(archive).use { zip ->
            // FTS 影子表由 Room 与触发器维护，不能作为独立数据导出。
            assertTrue(zip.entries().asSequence().none { it.name.contains("search_chunks_fts") })
            assertTrue(zip.getEntry("tables/search_chunks.jsonl") != null)
            assertTrue(zip.getEntry("manifest.json") != null)
            assertTrue(zip.entries().asSequence().none { it.name.contains("room_master_table") })
            val schemaEntry = zip.getEntry("app-data/database-schema.json")
            val schema = JSONObject(zip.getInputStream(schemaEntry).bufferedReader().use { it.readText() })
            assertTrue(
                schema.getJSONArray("objects").let { objects ->
                    (0 until objects.length()).none {
                        objects.getJSONObject(it).optString("name") == "room_master_table"
                    }
                }
            )
        }
        db.execSQL("DELETE FROM search_chunks")
        db.execSQL("DELETE FROM backup_probe")
        File(context.filesDir, "manifest.json").writeText("changed")
        PortableBackupRepository(
            mContext = context,
            mDatabase = primary,
            mRequestLogDatabase = logs,
            mLegacyRepository = BackupArchiveRepository(
                mContext = context,
                mDatabase = primary,
            ),
        ).restoreArchive(Uri.fromFile(archive))
        openDatabases()
        // 重新打开 Room 验证结构身份，并直接查询恢复后的倒排索引。
        val restored = primary.openHelper.writableDatabase
        restored.query("SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertFalse(cursor.moveToNext())
            }
        restored.query("SELECT cursor FROM search_progress WHERE kind = -2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2L, cursor.getLong(0))
        }
        restored.query("SELECT hex(payload), note FROM backup_probe WHERE id = 9").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("010203", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        logs.openHelper.readableDatabase.query("SELECT value FROM backup_log_probe").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("test log", cursor.getString(0))
        }
        assertEquals(
            "background",
            File(context.getDir("repository", 0), prepared.file.hash).readText()
        )
        assertEquals("private file", File(context.filesDir, "manifest.json").readText())
        assertTrue(FileRepository(context, primary).getFileEntity(prepared.file.uuid) != null)
    }

    /** 无效归档必须在关闭或覆盖任何当前数据库之前拒绝。 */
    @Test
    fun invalidArchiveLeavesLiveDataReadable() = runBlocking {
        primary.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe(value TEXT)")
        primary.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES('unchanged')")
        val archive = File(root, "invalid.zip").apply { writeText("invalid archive") }
        val result = runCatching {
            PortableBackupRepository(
                mContext = context,
                mDatabase = primary,
                mRequestLogDatabase = logs,
                mLegacyRepository = BackupArchiveRepository(
                    mContext = context,
                    mDatabase = primary,
                ),
            ).restoreArchive(Uri.fromFile(archive))
        }
        assertTrue(result.isFailure)
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("unchanged", cursor.getString(0))
        }
    }

    /** FTS4 的完整性检查在部分系统上需要可写连接，暂存库不能被只读校验误拒绝。 */
    @Test
    fun stagedFtsDatabasePassesValidation() {
        val staged = File(root, "staged-fts.sqlite")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val candidate = Room.databaseBuilder(base, AppDatabase::class.java, staged.path).build()
        try {
            candidate.openHelper.writableDatabase.execSQL(
                sql = "INSERT INTO search_chunks(rowid, kind, sourceId, parentId, revision, position, tokens) " +
                    "VALUES(42, 1, 7, 3, 1, 0, 'hello world')",
            )
        } finally {
            candidate.close()
        }

        BackupDatabaseValidator.validatePrimary(context, staged)

        val reopened = Room.databaseBuilder(base, AppDatabase::class.java, staged.path).build()
        try {
            reopened.openHelper.readableDatabase.query(
                query = "SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(42L, cursor.getLong(0))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            reopened.close()
        }
    }

    /** 高版本备份不能触发应用的降级重建，也不能提前关闭当前连接。 */
    @Test
    fun futureDatabaseVersionLeavesLiveDataReadable() = runBlocking {
        assertRejectedBeforeReplacement("app-data/database-schema.json") { text ->
            JSONObject(text).put("userVersion", 999).toString()
        }
    }

    /** 结构错误的行不能被静默跳过，否则会产生不完整恢复。 */
    @Test
    fun malformedRowsLeaveLiveDataReadable() = runBlocking {
        assertRejectedBeforeReplacement("tables/backup_probe.jsonl") { "not json\n" }
    }

    /** 手动编辑角色卡、删除另一个角色文件后，恢复结果必须以文件内容为准。 */
    @Test
    fun editedAndDeletedCharacterFilesAreHonoured() = runBlocking {
        val db = primary.openHelper.writableDatabase
        val columns = "id, name, avatar, characterTags, description, personality, scenario, " +
            "firstMessages, examplesOfDialogue, postHistoryInstructions"
        db.execSQL("INSERT INTO character($columns) VALUES(5, 'Alice', '', '[]', 'desc', '', '', 'Hello', '', '')")
        db.execSQL("INSERT INTO character($columns) VALUES(6, 'Bob', '', '[]', 'desc', '', '', 'Hello', '', '')")
        db.execSQL(
            "INSERT INTO chat_sessions(id, characterId, createTime, latestTime, lorebookEntrySet, title, " +
                "userNote, userName, userDescription, worldInfoStateJson, autoSummaryPaused, includeCharacterExamples) " +
                "VALUES(15, 5, 1, 2, '', 'Alice chat', '', 'User', '', '{}', 0, 0)"
        )
        db.execSQL(
            "INSERT INTO chat_sessions(id, characterId, createTime, latestTime, lorebookEntrySet, title, " +
                "userNote, userName, userDescription, worldInfoStateJson, autoSummaryPaused, includeCharacterExamples) " +
                "VALUES(16, 6, 1, 2, '', 'Bob chat', '', 'User', '', '{}', 0, 1)"
        )
        val archive = File(root, "snapshot.zip")
        val repository = PortableBackupRepository(context, primary, logs, BackupArchiveRepository(context, primary))
        repository.exportArchive(Uri.fromFile(archive))
        val unpacked = File(root, "unpacked").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(context, Uri.fromFile(archive), unpacked)
        val cards = File(unpacked, "characters").listFiles().orEmpty()
        val alice = cards.first { it.name.startsWith("Alice-") }
        val bob = cards.first { it.name.startsWith("Bob-") }
        val card = JSONObject(alice.readText())
        card.getJSONObject("data").put("name", "Alicia")
        alice.writeText(card.toString())
        assertTrue(bob.delete())
        zipDirectory(unpacked, archive)

        val report = BackupReport()
        repository.restoreArchive(Uri.fromFile(archive), report)
        openDatabases()
        primary.openHelper.readableDatabase.query("SELECT id, name FROM character ORDER BY id").use {
            assertTrue(it.moveToFirst())
            assertEquals(5L, it.getLong(0))
            assertEquals("Alicia", it.getString(1))
            assertFalse(it.moveToNext())
        }
        assertTrue(report.text().isNotBlank())
        primary.openHelper.readableDatabase.query(
            "SELECT title, includeCharacterExamples FROM chat_sessions ORDER BY id"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alice chat", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertFalse(cursor.moveToNext())
        }
        assertTrue(report.text().contains("chat skipped", ignoreCase = true))
    }

    /** 替换中途失败时，回滚快照必须包含关闭前仍位于 WAL 的已提交数据。 */
    @Test
    fun rollbackPreservesCommittedWalRows() = runBlocking {
        val database = primary.openHelper.writableDatabase
        database.execSQL("CREATE TABLE backup_probe(value TEXT)")
        database.execSQL("INSERT INTO backup_probe VALUES('committed before close')")
        val staged = File(root, "staged.sqlite").apply { writeText("replacement") }
        val other = File(root, "other").apply { writeText("other replacement") }
        val blocker = File(root, "blocked-parent").apply { writeText("not a directory") }
        // 先替换数据库，再通过不可写入的第二个目标确定性触发回滚。
        val result = runCatching {
            BackupDataReplacer.replace(
                rollback = File(root, "rollback"),
                replacements = listOf(
                    BackupReplacement(
                        source = staged,
                        target = context.getDatabasePath("primary.sqlite"),
                        isDatabase = true,
                    ),
                    BackupReplacement(other, File(blocker, "child")),
                ),
            ) { primary.close() }
        }
        assertTrue(result.exceptionOrNull() is BackupRestoreException)
        logs.close()
        openDatabases()
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("committed before close", cursor.getString(0))
        }
    }

    /** 修改归档内文件后重新打包，确认结构错误会在覆盖当前数据之前被拒绝。 */
    private suspend fun assertRejectedBeforeReplacement(
        path: String,
        transform: (String) -> String
    ) {
        val archive = File(root, "snapshot.zip")
        val repository = PortableBackupRepository(
            mContext = context,
            mDatabase = primary,
            mRequestLogDatabase = logs,
            mLegacyRepository = BackupArchiveRepository(
                mContext = context,
                mDatabase = primary,
            ),
        )
        primary.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe(value TEXT)")
        primary.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES('unchanged')")
        repository.exportArchive(Uri.fromFile(archive))
        val unpacked = File(root, "unpacked").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(context, Uri.fromFile(archive), unpacked)
        val changed = File(unpacked, path)
        changed.writeText(transform(changed.readText()))
        zipDirectory(unpacked, archive)
        assertTrue(runCatching { repository.restoreArchive(Uri.fromFile(archive)) }.isFailure)
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("unchanged", cursor.getString(0))
        }
    }

    private fun openDatabases() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        primary = Room.databaseBuilder(
            base,
            AppDatabase::class.java,
            context.getDatabasePath("primary.sqlite").path
        )
            .addCallback(SearchDatabaseCallback)
            .build()
        logs = Room.databaseBuilder(
            context = base,
            klass = RequestLogDatabase::class.java,
            name = context.getDatabasePath("request_logs.sqlite").path,
        ).build()
    }

    /** 把解包目录重新压成 ZIP，模拟用户用外部工具编辑后的归档。 */
    private fun zipDirectory(directory: File, target: File) {
        ZipOutputStream(target.outputStream()).use { zip ->
            directory.walkTopDown().drop(1).forEach { file ->
                val name = file.relativeTo(directory).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(if (file.isDirectory) "$name/" else name))
                if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}
