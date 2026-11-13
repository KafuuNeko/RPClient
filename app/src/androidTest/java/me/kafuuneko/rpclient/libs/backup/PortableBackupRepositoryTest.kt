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
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.appearance.BackgroundPage
import me.kafuuneko.rpclient.libs.appearance.PageBackground
import me.kafuuneko.rpclient.libs.prompt.PromptType
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.ChatSession
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.room.repository.PageBackgroundRepository
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.room.repository.StoryRepository
import me.kafuuneko.rpclient.libs.room.repository.UserPersonaRepository
import me.kafuuneko.rpclient.libs.room.withTestPersonas
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    private var previousPresetId = -1L

    /** 所有数据库和文件均位于本项测试的临时目录。 */
    @Before
    fun setUp() {
        previousPresetId = AppModel.activePromptPresetId
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
        AppModel.activePromptPresetId = previousPresetId
        primary.close()
        logs.close()
        root.deleteRecursively()
    }

    /** 备份只保存业务行与文件，搜索派生数据不导出，完整恢复后默认关闭加速。 */
    @Test
    fun roundTripPreservesBusinessBackgroundAndPrivateFilesWithoutSearchData() = runBlocking {
        val presets = PromptPresetRepository(primary)
        val presetId = presets.create(name = "Backup template", description = "Whole-set copy")
        presets.savePrompt(presetId, 1, PromptType.Main, "")
        presets.savePrompt(presetId, 2, PromptType.StoryMain, "\n  {{char}} custom story  \n")
        presets.activate(presetId)
        File(
            root,
            "shared_prefs/" + AppModel.kotprefName + ".xml"
        ).writeText("<map><long name=\"activePromptPresetId\" value=\"$presetId\" /></map>")
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
        db.execSQL("INSERT INTO search_sources(kind, sourceId, parentId, revision, indexedRevision, deleted, dirty, changedTime) VALUES(1, 7, 3, 1, 1, 0, 0, 0)")
        SearchDatabaseCallback.onOpen(db)
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
            // 词条、来源、进度和 FTS 物理存储都可重建，不进入备份的数据文件。
            assertTrue(zip.entries().asSequence().none { it.name.contains("search_chunks_fts") })
            assertTrue(zip.getEntry("tables/search_chunks.jsonl") == null)
            assertTrue(zip.getEntry("tables/search_sources.jsonl") == null)
            assertTrue(zip.getEntry("tables/search_progress.jsonl") == null)
            assertTrue(zip.getEntry("manifest.json") != null)
            assertTrue(zip.entries().asSequence().none { it.name.contains("room_master_table") })
            val schemaEntry = zip.getEntry("app-data/database-schema.json")
            val schema =
                JSONObject(zip.getInputStream(schemaEntry).bufferedReader().use { it.readText() })
            assertTrue(
                schema.getJSONArray("objects").let { objects ->
                    (0 until objects.length()).none {
                        val name = objects.getJSONObject(it).optString("name")
                        name == "room_master_table" || name.startsWith("search_source_")
                            || name.startsWith("search_meta_")
                    }
                }
            )
        }
        presets.delete(presetId)
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
        // 正文与 SP 选择分别随完整备份恢复，空串不能变回默认值。
        val restoredPreset = PromptPresetRepository(primary).getDetail(presetId).snapshot
        assertTrue(
            File(root, "shared_prefs/" + AppModel.kotprefName + ".xml").readText()
                .contains("value=\"$presetId\"")
        )
        assertEquals(presetId, restoredPreset.presetId)
        assertEquals(3L, restoredPreset.revision)
        assertEquals("", restoredPreset.mainPrompt)
        assertEquals("\n  {{char}} custom story  \n", restoredPreset.storyMainPrompt)
        // 空检索结构仍须通过 Room 校验；之前的启用状态也不会随归档恢复。
        val restored = primary.openHelper.writableDatabase
        restored.query("SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'")
            .use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
        restored.query("SELECT cursor FROM search_progress WHERE kind = -2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        restored.query("SELECT (SELECT COUNT(*) FROM search_chunks), (SELECT COUNT(*) FROM search_sources)")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
                assertEquals(0L, cursor.getLong(1))
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

    /** FTS4 暂存库正确通过完整性校验，恢复准备后仅保留可供手动重建的空结构。 */
    @Test
    fun stagedFtsDatabasePassesValidationAndStartsWithSearchDisabled() {
        val staged = File(root, "staged-fts.sqlite")
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val candidate = Room.databaseBuilder(base, AppDatabase::class.java, staged.path).build()
        try {
            candidate.openHelper.writableDatabase.execSQL(
                sql = "INSERT INTO search_chunks(rowid, kind, sourceId, parentId, revision, position, tokens) " +
                    "VALUES(42, 1, 7, 3, 1, 0, 'hello world')",
            )
            candidate.openHelper.writableDatabase.execSQL("INSERT INTO search_progress(kind, cursor, complete) VALUES(-2, 2, 1)")
        } finally {
            candidate.close()
        }

        BackupDatabaseValidator.preparePrimaryForRestore(context, staged)

        val reopened = Room.databaseBuilder(base, AppDatabase::class.java, staged.path).build()
        try {
            reopened.openHelper.readableDatabase.query(
                query = "SELECT docid FROM search_chunks_fts WHERE search_chunks_fts MATCH 'hello'",
            ).use { cursor ->
                assertFalse(cursor.moveToFirst())
            }
            reopened.openHelper.readableDatabase.query("SELECT cursor FROM search_progress WHERE kind = -2")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0L, cursor.getLong(0))
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

    /** 完整备份保留多开场白的空段与空白，旧 v3 未带扩展的角色仍可恢复。 */
    @Test
    fun characterGreetingsRoundTripWithoutLosingLocalSegments() = runBlocking {
        val greetings = listOf(
            "  Hello\n<START> Second🙂 <START><START> Third <START>",
            "<START>Second",
            "Legacy hello",
            ""
        )
        val database = primary.openHelper.writableDatabase
        // 同时保留备用开场白，确认附加主开场白不会被合并到另一个业务字段。
        greetings.forEachIndexed { index, firstMessages ->
            database.execSQL(
                "INSERT INTO character(id, name, avatar, characterTags, description, personality, scenario, " +
                    "firstMessages, examplesOfDialogue, postHistoryInstructions, alternateGreetings) " +
                    "VALUES(?, ?, '', '[]', '', '', '', ?, '', '', ?)",
                arrayOf<Any>(index + 1, "Greeting-$index", firstMessages, "[\"Alternate\"]")
            )
        }
        val archive = File(root, "greetings.zip")
        val repository = PortableBackupRepository(
            context,
            primary,
            logs,
            BackupArchiveRepository(context, primary)
        )
        repository.exportArchive(Uri.fromFile(archive))
        val unpacked = File(root, "unpacked-greetings").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(context, Uri.fromFile(archive), unpacked)
        val legacyCard = File(unpacked, "characters/Greeting-2-3.json")
        val legacyDocument = JSONObject(legacyCard.readText())
        legacyDocument.getJSONObject("rpclient_backup").remove("additionalFirstMessages")
        legacyCard.writeText(legacyDocument.toString())
        zipDirectory(unpacked, archive)

        // 经过真实数据库替换与重新打开后验证原始字段，避免仅验证序列化表面结构。
        repository.restoreArchive(Uri.fromFile(archive))
        openDatabases()
        primary.openHelper.readableDatabase.query(
            "SELECT firstMessages, alternateGreetings FROM character ORDER BY id"
        ).use { cursor ->
            greetings.forEach { expected ->
                assertTrue(cursor.moveToNext())
                assertEquals(expected, cursor.getString(0))
                assertEquals("[\"Alternate\"]", cursor.getString(1))
            }
            assertFalse(cursor.moveToNext())
        }
    }

    /** 大于游标窗口的双正文保留跨块 Unicode、NUL 和扩展列，且能完整恢复。 */
    @Test
    fun largeRequestLogsRoundTripWithoutCursorWindowOverflow() = runBlocking {
        val request = "a".repeat(65_535) + "🙂猫\u0000" + "猫🙂".repeat(450_000)
        val response = "b".repeat(65_535) + "猫🙂\u0000" + "🙂猫".repeat(450_000)
        val database = logs.openHelper.writableDatabase
        database.execSQL(
            "INSERT INTO llm_request_logs(id, createTime, providerName, providerType, protocol, model, " +
                "isStreaming, requestJson, responseJson) " +
                "VALUES(7, 1, 'test', 'Custom', 'OpenAICompatible', 'model', 0, ?, ?)",
            arrayOf(request, response)
        )
        // 兼容旧调用的批量读取入口也必须使用与逐行导出相同的分块策略。
        val row =
            BackupDatabaseCodec.readRows(database, "llm_request_logs", "id", 7).getJSONObject(0)
        assertEquals(request, row.getString("requestJson"))
        assertEquals(response, row.getString("responseJson"))
        val archive = File(root, "large-logs.zip")
        val repository = PortableBackupRepository(
            context,
            primary,
            logs,
            BackupArchiveRepository(context, primary)
        )
        repository.exportArchive(Uri.fromFile(archive))
        repository.restoreArchive(Uri.fromFile(archive))
        openDatabases()

        // 仍通过安全入口读取，测试断言自身也不能把大正文装入普通 CursorWindow。
        val restored =
            BackupDatabaseCodec.readRows(logs.openHelper.readableDatabase, "llm_request_logs")
        assertEquals(1, restored.length())
        assertEquals(request, restored.getJSONObject(0).getString("requestJson"))
        assertEquals(response, restored.getJSONObject(0).getString("responseJson"))
        // 将来增加的小字段也应留在投影中，不因修复大正文而被固定列清单遗漏。
        val restoredDatabase = logs.openHelper.writableDatabase
        restoredDatabase.execSQL("ALTER TABLE llm_request_logs ADD COLUMN backup_extra TEXT")
        restoredDatabase.execSQL("UPDATE llm_request_logs SET backup_extra = 'extra' WHERE id = 7")
        BackupDatabaseCodec.forEachRow(restoredDatabase, "llm_request_logs") {
            assertEquals("extra", it.getString("backup_extra"))
            assertEquals(request, it.getString("requestJson"))
        }
    }

    /** 单条正文超过读取预算时明确拒绝，目标旧备份不能被部分归档覆盖。 */
    @Test
    fun oversizedLogPayloadDoesNotOverwriteDestination() = runBlocking {
        val database = logs.openHelper.writableDatabase
        database.execSQL(
            "INSERT INTO llm_request_logs(id, createTime, providerName, providerType, protocol, model, " +
                "isStreaming, requestJson, responseJson) " +
                "VALUES(7, 1, 'test', 'Custom', 'OpenAICompatible', 'model', 0, CAST(zeroblob(16777217) AS TEXT), '')"
        )
        val archive = File(root, "existing.zip").apply { writeText("existing backup") }
        val repository = PortableBackupRepository(
            context,
            primary,
            logs,
            BackupArchiveRepository(context, primary)
        )

        // 导出先完整生成临时 ZIP，预算失败不能触碰系统选择器返回的目标文件。
        assertTrue(runCatching { repository.exportArchive(Uri.fromFile(archive)) }.isFailure)
        assertEquals("existing backup", archive.readText())
        assertTrue(context.cacheDir.listFiles().orEmpty().isEmpty())
    }

    /** 手动编辑角色卡、删除另一个角色文件后，恢复结果必须以文件内容为准。 */
    @Test
    fun editedAndDeletedCharacterFilesAreHonoured() = runBlocking {
        val db = primary.openHelper.writableDatabase
        val columns = "id, name, avatar, characterTags, description, personality, scenario, " +
            "firstMessages, examplesOfDialogue, postHistoryInstructions"
        db.execSQL("INSERT INTO character($columns) VALUES(5, 'Alice', '', '[]', 'desc', '', '', 'Hello', '', '')")
        db.execSQL("INSERT INTO character($columns) VALUES(6, 'Bob', '', '[]', 'desc', '', '', 'Hello', '', '')")
        db.execSQL("UPDATE character SET firstMessages = 'Hello<START>Second<START>Third' WHERE id = 5")
        db.execSQL(
            "INSERT INTO chat_sessions(id, characterId, createTime, latestTime, lorebookEntrySet, title, " +
                "userNote, userName, userDescription, worldInfoStateJson, autoSummaryPaused, includeCharacterExamples, userPersonaId) " +
                "VALUES(15, 5, 1, 2, '', 'Alice chat', '', 'User', '', '{}', 0, 0, 1)"
        )
        db.execSQL(
            "INSERT INTO chat_sessions(id, characterId, createTime, latestTime, lorebookEntrySet, title, " +
                "userNote, userName, userDescription, worldInfoStateJson, autoSummaryPaused, includeCharacterExamples, userPersonaId) " +
                "VALUES(16, 6, 1, 2, '', 'Bob chat', '', 'User', '', '{}', 0, 1, 1)"
        )
        val archive = File(root, "snapshot.zip")
        val repository = PortableBackupRepository(
            context,
            primary,
            logs,
            BackupArchiveRepository(context, primary)
        )
        repository.exportArchive(Uri.fromFile(archive))
        val unpacked = File(root, "unpacked").apply { mkdirs() }
        BackupArchiveFiles.extractArchive(context, Uri.fromFile(archive), unpacked)
        val cards = File(unpacked, "characters").listFiles().orEmpty()
        val alice = cards.first { it.name.startsWith("Alice-") }
        val bob = cards.first { it.name.startsWith("Bob-") }
        val card = JSONObject(alice.readText())
        card.getJSONObject("data").put("name", "Alicia")
        card.getJSONObject("data").put("first_mes", "Edited first")
        alice.writeText(card.toString())
        assertTrue(bob.delete())
        zipDirectory(unpacked, archive)

        val report = BackupReport()
        repository.restoreArchive(Uri.fromFile(archive), report)
        openDatabases()
        primary.openHelper.readableDatabase.query("SELECT id, name, firstMessages FROM character ORDER BY id")
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(5L, it.getLong(0))
                assertEquals("Alicia", it.getString(1))
                assertEquals("Edited first<START>Second<START>Third", it.getString(2))
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

    /** 完整归档应保存独立默认项、三个入口的绑定及两个覆盖列的原始状态。 */
    @Test
    fun personaBindingsAndNullableOverridesSurviveRestore() = runBlocking {
        val personas = UserPersonaRepository(primary, FileRepository(context, primary))
        val initial = personas.active().id
        val second = personas.create("Mira")
        personas.describe(second, "Archivist")
        val character = primary.getCharacterDao().insertOrReplace(
            Character(
                name = "Guide",
                avatar = "",
                characterTags = "[]",
                description = "",
                personality = "",
                scenario = "",
                firstMessages = "Welcome",
                examplesOfDialogue = "",
                postHistoryInstructions = ""
            )
        )
        val session = ChatSession(
            characterId = character, createTime = 1, latestTime = 1,
            title = "original", lorebookEntrySet = "[]", userNote = "", userPersonaId = second,
            userDescription = " {{user}} "
        )
        val chat = primary.getChatSessionDao().insertOrReplace(session)
        val alias = primary.getChatSessionDao().insertOrReplace(
            session.copy(title = "alias", userName = "  Alias  ", userDescription = "")
        )
        val group = primary.getGroupChatSessionDao().insertOrReplace(
            GroupChatSession(
                title = "Group",
                createTime = 1,
                latestTime = 1,
                userPersonaId = initial,
                userName = "Captain"
            )
        )
        val writer = StoryRepository(primary)
        val story = writer.createStoryWithConfiguration(
            "Story",
            emptyList(),
            emptyList(),
            userPersonaId = second
        )
        val disabled = writer.createStory("Other")
        val backup = PortableBackupRepository(
            context,
            primary,
            logs,
            BackupArchiveRepository(context, primary)
        )
        val uri = Uri.fromFile(File(root, "personas.zip"))
        backup.exportArchive(uri)
        personas.select(initial)
        personas.describe(second, "changed")
        backup.restoreArchive(uri)
        openDatabases()
        val dao = primary.getUserPersonaDao()
        assertEquals(second, dao.active().id)
        assertEquals("Archivist", dao.active().description)
        val chats = primary.getChatSessionDao()
        val inherited = requireNotNull(chats.getSessionById(chat))
        assertEquals(null, inherited.userName)
        assertEquals(" {{user}} ", inherited.userDescription)
        assertEquals("Mira", dao.resolve(inherited).name)
        val overridden = requireNotNull(chats.getSessionById(alias))
        assertEquals("  Alias  ", overridden.userName)
        assertEquals("", overridden.userDescription)
        assertEquals("Archivist", dao.resolve(overridden).description)
        val restoredGroup =
            primary.getGroupChatSessionDao().getAllSessions().single { it.id == group }
        assertEquals(initial, restoredGroup.userPersonaId)
        assertEquals("Captain", restoredGroup.userName)
        assertEquals(null, restoredGroup.userDescription)
        assertEquals(second, primary.getStoryDao().getStory(story)?.userPersonaId)
        assertEquals(-1L, primary.getStoryDao().getStory(disabled)?.userPersonaId)
    }

    /** 完整恢复保留同图的独立 UUID，恢复后清空人设头像仍可读取角色头像。 */
    @Test
    fun personaAvatarRestorePreservesIndependentFileReferences() = runBlocking {
        val files = FileRepository(context, primary)
        val personas = UserPersonaRepository(primary, files)
        val personaId = personas.active().id
        val bytes = "shared avatar".toByteArray()
        val personaAvatar = files.saveFile(bytes, "image/png")
        val characterAvatar = files.saveFile(bytes, "image/png")
        assertNotEquals(personaAvatar, characterAvatar)
        personas.setAvatar(personaId, personaAvatar)
        val characterId = primary.getCharacterDao().insertOrReplace(
            Character(
                name = "Guide", avatar = characterAvatar, characterTags = "[]",
                description = "", personality = "", scenario = "", firstMessages = "",
                examplesOfDialogue = "", postHistoryInstructions = ""
            )
        )

        // 两处引用在完整归档中保留各自的 UUID，共享的图片字节仅存一份。
        val backup = PortableBackupRepository(
            context, primary, logs, BackupArchiveRepository(context, primary)
        )
        val uri = Uri.fromFile(File(root, "persona-avatars.zip"))
        backup.exportArchive(uri)
        backup.restoreArchive(uri)
        openDatabases()
        val restored = FileRepository(context, primary)
        assertEquals(personaAvatar, primary.getUserPersonaDao().get(personaId).avatarFileUuid)
        assertEquals(
            characterAvatar,
            primary.getCharacterDao().getCharacterById(characterId)?.avatar
        )
        val hash = requireNotNull(restored.getFileEntity(personaAvatar)).hash
        assertEquals(hash, restored.getFileEntity(characterAvatar)?.hash)
        assertEquals(2, primary.getFileDao().countByHash(hash))

        // 使用恢复后的仓库清空头像，角色索引及原图均应保留。
        UserPersonaRepository(primary, restored).setAvatar(personaId, null)
        assertNull(primary.getUserPersonaDao().get(personaId).avatarFileUuid)
        assertNull(restored.getFileEntity(personaAvatar))
        assertEquals(1, primary.getFileDao().countByHash(hash))
        assertArrayEquals(bytes, requireNotNull(restored.getFile(characterAvatar)).readBytes())
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
        ).withTestPersonas()
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
