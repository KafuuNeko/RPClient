package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
            override fun getDir(name: String, mode: Int): File = File(root, "app_$name").apply { mkdirs() }
            override fun getDatabasePath(name: String): File =
                (if (File(name).isAbsolute) File(name) else File(root, "databases/$name")).also {
                    it.parentFile?.mkdirs()
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
        db.execSQL("INSERT INTO search_chunks(rowid, kind, sourceId, parentId, revision, position, tokens) VALUES(42, 1, 7, 3, 1, 0, 'hello world')")
        db.execSQL("INSERT OR REPLACE INTO search_progress(kind, cursor, complete) VALUES(-2, 2, 1)")
        db.execSQL("INSERT INTO search_progress(kind, cursor, complete) VALUES(-1, ?, 1)", arrayOf(SearchTextCodec.FORMAT_VERSION))
        for (kind in 1..3) db.execSQL("INSERT INTO search_progress(kind, cursor, complete) VALUES(?, 7, 1)", arrayOf(kind))
        db.execSQL("CREATE TABLE backup_probe(id INTEGER PRIMARY KEY, payload BLOB, note TEXT)")
        db.execSQL("INSERT INTO backup_probe VALUES(9, X'010203', NULL)")
        val logDb = logs.openHelper.writableDatabase
        logDb.execSQL("CREATE TABLE backup_log_probe(value TEXT)")
        logDb.execSQL("INSERT INTO backup_log_probe VALUES('test log')")
        // 背景文件通过实际仓库提交，验证新增关系表和附件索引不会丢失。
        val files = FileRepository(context, primary)
        val prepared = files.prepareStream("backup-test", ByteArrayInputStream("background".toByteArray()), "image/png")
        PageBackgroundRepository(primary, files).save(PageBackground(BackgroundPage.Story, blur = 37), prepared)
        File(context.filesDir, "manifest.json").writeText("private file")
        val archive = File(root, "snapshot.zip")
        PortableBackupRepository(context, primary, logs, BackupArchiveRepository(context, primary)).exportArchive(Uri.fromFile(archive))
        ZipFile(archive).use { zip ->
            val rows = zip.getInputStream(zip.getEntry("app-data/other-tables.jsonl")).reader().readText()
            assertFalse(rows.contains("search_chunks_fts_segments"))
        }
        db.execSQL("DELETE FROM search_chunks")
        db.execSQL("DELETE FROM backup_probe")
        File(context.filesDir, "manifest.json").writeText("changed")
        PortableBackupRepository(context, primary, logs, BackupArchiveRepository(context, primary)).restoreArchive(Uri.fromFile(archive))
        openDatabases()
        // 重新打开 Room 验证结构身份，并直接查询恢复后的倒排索引。
        val restored = primary.openHelper.writableDatabase
        restored.query("SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'").use {
            assertTrue(it.moveToFirst())
            assertEquals(42L, it.getLong(0))
            assertFalse(it.moveToNext())
        }
        restored.query("SELECT cursor FROM search_progress WHERE kind = -2").use {
            assertTrue(it.moveToFirst())
            assertEquals(2L, it.getLong(0))
        }
        restored.query("SELECT hex(payload), note FROM backup_probe WHERE id = 9").use {
            assertTrue(it.moveToFirst())
            assertEquals("010203", it.getString(0))
            assertTrue(it.isNull(1))
        }
        logs.openHelper.readableDatabase.query("SELECT value FROM backup_log_probe").use {
            assertTrue(it.moveToFirst())
            assertEquals("test log", it.getString(0))
        }
        assertEquals("background", File(context.getDir("repository", 0), prepared.file.hash).readText())
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
            PortableBackupRepository(context, primary, logs, BackupArchiveRepository(context, primary)).restoreArchive(Uri.fromFile(archive))
        }
        assertTrue(result.isFailure)
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use {
            assertTrue(it.moveToFirst())
            assertEquals("unchanged", it.getString(0))
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
                "INSERT INTO search_chunks(rowid, kind, sourceId, parentId, revision, position, tokens) " +
                    "VALUES(42, 1, 7, 3, 1, 0, 'hello world')"
            )
        } finally {
            candidate.close()
        }

        BackupDatabaseValidator.validatePrimary(context, staged)

        val reopened = Room.databaseBuilder(base, AppDatabase::class.java, staged.path).build()
        try {
            reopened.openHelper.readableDatabase.query(
                "SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'"
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(42L, it.getLong(0))
                assertFalse(it.moveToNext())
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

    /** 校验和正确但结构错误的行不能被静默跳过，否则会产生不完整恢复。 */
    @Test
    fun malformedRowsLeaveLiveDataReadable() = runBlocking {
        assertRejectedBeforeReplacement("app-data/other-tables.jsonl") {
            "{\"_tableRows\":{\"search_chunks\":\"invalid rows\"}}\n"
        }
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
            BackupDataReplacer.replace(File(root, "rollback"), listOf(
                BackupReplacement(staged, context.getDatabasePath("primary.sqlite"), isDatabase = true),
                BackupReplacement(other, File(blocker, "child"))
            )) { primary.close() }
        }
        assertTrue(result.exceptionOrNull() is BackupRestoreException)
        logs.close()
        openDatabases()
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use {
            assertTrue(it.moveToFirst())
            assertEquals("committed before close", it.getString(0))
        }
    }

    /** 修改内容后重算索引，确保测试命中结构校验，而非仅命中校验和失败。 */
    private suspend fun assertRejectedBeforeReplacement(path: String, transform: (String) -> String) {
        val archive = File(root, "snapshot.zip")
        val repository = PortableBackupRepository(context, primary, logs, BackupArchiveRepository(context, primary))
        primary.openHelper.writableDatabase.execSQL("CREATE TABLE backup_probe(value TEXT)")
        primary.openHelper.writableDatabase.execSQL("INSERT INTO backup_probe VALUES('unchanged')")
        repository.exportArchive(Uri.fromFile(archive))
        val unpacked = File(root, "unpacked").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(context, Uri.fromFile(archive), unpacked)
        val changed = File(unpacked, path)
        changed.writeText(transform(changed.readText()))
        val manifestFile = File(unpacked, "manifest.json")
        val manifest = JSONObject(manifestFile.readText())
        manifest.getJSONObject("files").getJSONObject(path)
            .put("size", changed.length()).put("sha256", BackupArchiveFiles.sha256(changed))
        manifestFile.writeText(manifest.toString())
        ZipOutputStream(archive.outputStream()).use { zip ->
            unpacked.walkTopDown().drop(1).forEach { file ->
                val name = file.relativeTo(unpacked).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(if (file.isDirectory) "$name/" else name))
                if (file.isFile) file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        assertTrue(runCatching { repository.restoreArchive(Uri.fromFile(archive)) }.isFailure)
        primary.openHelper.readableDatabase.query("SELECT value FROM backup_probe").use {
            assertTrue(it.moveToFirst())
            assertEquals("unchanged", it.getString(0))
        }
    }

    private fun openDatabases() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        primary = Room.databaseBuilder(base, AppDatabase::class.java, context.getDatabasePath("primary.sqlite").path).addCallback(SearchDatabaseCallback).build()
        logs = Room.databaseBuilder(base, RequestLogDatabase::class.java, context.getDatabasePath("request_logs.sqlite").path).build()
    }
}
