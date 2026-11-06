package me.kafuuneko.rpclient.libs.prompt

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetConflictException
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.upgrade.LegacyPromptSnapshot
import me.kafuuneko.rpclient.libs.upgrade.PromptPresetMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证旧库迁移、稀疏覆盖和编辑冲突，不依赖默认预设的数据库记录。 */
@RunWith(AndroidJUnit4::class)
class PromptPresetMigrationTest {
    private val mContext = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /** 所有支持的旧结构都迁到当前 v6，旧用户文件与原文保持不变。 */
    @Test
    fun allSupportedSchemasReachSixWithoutLosingFiles() {
        for (version in 1..5) {
            val name = "prompt-migration-$version"
            helper.createDatabase(name, version).apply {
                execSQL("INSERT INTO files (uuid, hash, mimeType) VALUES ('preserved', 'hash', 'image/png')")
                close()
            }
            val db = helper.runMigrationsAndValidate(name, 6, true)
            // 默认项不入库，仅导入旧配置中实际保存的空串覆盖。
            PromptPresetMigration.migrate(
                db,
                { LegacyPromptSnapshot.read(mapOf("mainPrompt" to "")) },
                "Custom preset 1"
            )
            db.query("SELECT uuid FROM files")
                .use { assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)) }
            db.query("SELECT content FROM prompt_preset_entries WHERE promptKey = 'main'")
                .use { assertTrue(it.moveToFirst()); assertEquals("", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM prompt_preset_entries")
                .use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM prompt_presets WHERE id = -1")
                .use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.close()
        }
    }

    /** 数据库提交后尚未保存 SP 时重试，复用原记录且不覆盖已保存的内容。 */
    @Test
    fun committedImportIsReusedAndOldSnapshotsStayUnchanged() = runBlocking {
        withDatabase { database ->
            val db = database.openHelper.writableDatabase
            val id = PromptPresetMigration.migrate(
                db,
                { LegacyPromptSnapshot.read(mapOf("mainPrompt" to "\nold {{char}}\n")) },
                "Custom preset 1"
            )
            val repository = PromptPresetRepository(database)
            val initial = repository.getDetail(id).snapshot
            repository.savePrompt(id, initial.revision, PromptType.Main, "new")
            assertEquals(
                id,
                PromptPresetMigration.migrate(
                    db,
                    { error("Must reuse committed import") },
                    "Custom preset 1"
                )
            )
            assertEquals("new", repository.getDetail(id).snapshot.mainPrompt)
            assertEquals("\nold {{char}}\n", initial.mainPrompt)
        }
    }

    /** 写入中断时元数据与覆盖项全部回滚，原快照可以重新导入。 */
    @Test
    fun failedImportRollsBackMetadataAndEntries() {
        withDatabase { database ->
            val db = database.openHelper.writableDatabase
            db.execSQL("CREATE TRIGGER fail_prompt BEFORE INSERT ON prompt_preset_entries BEGIN SELECT RAISE(ABORT, 'simulated write failure'); END")
            assertTrue(runCatching {
                PromptPresetMigration.migrate(
                    db,
                    { LegacyPromptSnapshot.read(mapOf("mainPrompt" to "kept")) },
                    "Custom preset 1"
                )
            }.isFailure)
            for (table in listOf("prompt_presets", "prompt_preset_entries")) {
                db.query("SELECT COUNT(*) FROM $table")
                    .use { it.moveToFirst(); assertEquals(0L, it.getLong(0)) }
            }
            // 去除失败点后完整重试，不需要清空数据库。
            db.execSQL("DROP TRIGGER fail_prompt")
            assertEquals(
                1L,
                PromptPresetMigration.migrate(
                    db,
                    { LegacyPromptSnapshot.read(mapOf("mainPrompt" to "kept")) },
                    "Custom preset 1"
                )
            )
        }
    }

    /** 默认回退、覆盖保存、复制和恢复默认共同保证条目表只保存差异。 */
    @Test
    fun sparseOverridesCopyIndependentlyAndDeletingActiveUsesDefaultSpSelection() = runBlocking {
        val previous = AppModel.activePromptPresetId
        try {
            withDatabase { database ->
                val repository = PromptPresetRepository(database)
                val id = repository.create(name = "Custom", description = "")
                val dao = database.getPromptPresetDao()
                assertTrue(dao.getEntries(id).isEmpty())
                assertEquals(
                    PromptType.Main.defaultValue,
                    repository.getDetail(id).snapshot.mainPrompt
                )
                assertEquals(
                    PromptType.Main.defaultValue,
                    repository.getDetail(-1).snapshot.mainPrompt
                )
                repository.savePrompt(id, 1, PromptType.Main, "")
                // 旧 revision 不能部分覆盖正文；复制只带走一项差异。
                assertTrue(runCatching {
                    repository.savePrompt(
                        id,
                        1,
                        PromptType.Main,
                        "lost update"
                    )
                }.exceptionOrNull() is PromptPresetConflictException)
                val copy = repository.create(sourceId = id, name = "Copy", description = "")
                assertEquals(1, dao.getEntries(copy).size)
                repository.savePrompt(id, 2, PromptType.Main, PromptType.Main.defaultValue)
                assertTrue(dao.getEntries(id).isEmpty())
                assertEquals("", repository.getDetail(copy).snapshot.mainPrompt)
                repository.activate(copy)
                assertEquals(copy, AppModel.activePromptPresetId)
                repository.delete(copy)
                assertEquals(-1L, AppModel.activePromptPresetId)
                assertTrue(dao.getEntries(copy).isEmpty())
                assertEquals(-1L, repository.getActiveSnapshot().presetId)
            }
        } finally {
            AppModel.activePromptPresetId = previous
        }
    }

    /** 每个用例关闭自己的隔离库。 */
    private inline fun <T> withDatabase(block: (AppDatabase) -> T): T {
        val database = Room.inMemoryDatabaseBuilder(mContext, AppDatabase::class.java).build()
        try {
            return block(database)
        } finally {
            database.close()
        }
    }
}
