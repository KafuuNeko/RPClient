package me.kafuuneko.rpclient.libs.upgrade.steps

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.upgrade.AppUpgrade
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeManager
import me.kafuuneko.rpclient.libs.upgrade.AppUpgradeVersionStore
import me.kafuuneko.rpclient.libs.upgrade.AppVersionCodeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 使用隔离 Room 和偏好验证真实迁入逻辑的检查点与清理边界。 */
@RunWith(AndroidJUnit4::class)
class UserPersonaUpgradeFlowTest {
    /** 数据库提交后检查点失败，续跑必须保留后来的人设编辑。 */
    @Test
    fun checkpointFailurePreservesImportedPersonaOnRetry() = runBlocking {
        withStorage { db, prefs ->
            val store = Checkpoints().apply { failMigrationCheckpoint = true }
            val step = Upgrade20260404(
                db,
                InstrumentationRegistry.getInstrumentation().targetContext,
                prefs
            )
            val manager = manager(store, listOf(step))
            assertTrue(runCatching { manager.upgrade() }.isFailure)
            assertTrue(prefs.contains("userName"))
            assertEquals(20260103, store.lastCompletedVersionCode)
            // 模拟首次 SQL 已提交，检查点落盘前退出后仍有有效身份。
            db.getUserPersonaDao().rename(1, "Edited")
            store.failMigrationCheckpoint = false
            manager.upgrade()
            assertEquals("Edited", db.getUserPersonaDao().active().name)
            assertEquals(1, db.getUserPersonaDao().list().size)
            assertEquals(20260404, store.lastCleanedVersionCode)
            assertFalse(prefs.contains("userName"))
            assertEquals("keep", prefs.getString("unrelated", null))
        }
    }

    /** 后续版本失败时，人设迁移虽成功也必须等待全部迁移完成再清理。 */
    @Test
    fun laterMigrationFailureDefersPersonaCleanup() = runBlocking {
        withStorage { db, prefs ->
            val store = Checkpoints()
            var failLater = true
            val later = object : AppUpgrade {
                override val targetVersionCode = 20260406
                override suspend fun migrate() {
                    check(!failLater)
                }
            }
            val manager = manager(
                store,
                listOf(
                    Upgrade20260404(
                        db,
                        InstrumentationRegistry.getInstrumentation().targetContext,
                        prefs
                    ), later
                )
            )
            assertTrue(runCatching { manager.upgrade() }.isFailure)
            assertEquals(20260404, store.lastCompletedVersionCode)
            assertEquals(20260103, store.lastCleanedVersionCode)
            assertEquals("Legacy", db.getUserPersonaDao().active().name)
            assertTrue(prefs.contains("userDescription"))
            // 下一次启动完成剩余版本后，两个清理检查点才能一起推进。
            failLater = false
            manager.upgrade()
            assertFalse(prefs.contains("userDescription"))
            assertEquals(20260406, store.lastCleanedVersionCode)
        }
    }

    /** 清理提交失败只重试清理，既有默认选择及描述不能被旧键覆盖。 */
    @Test
    fun cleanupFailureRetriesWithoutReimporting() = runBlocking {
        withStorage { db, prefs ->
            var failCommit = true
            val failing = object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean = if (failCommit) false else editor.commit()
                    }
                }
            }
            val store = Checkpoints()
            val manager = manager(
                store,
                listOf(
                    Upgrade20260404(
                        db,
                        InstrumentationRegistry.getInstrumentation().targetContext,
                        failing
                    )
                )
            )
            assertTrue(runCatching { manager.upgrade() }.isFailure)
            assertEquals(20260404, store.lastCompletedVersionCode)
            assertEquals(20260103, store.lastCleanedVersionCode)
            // 迁移检查点已完成，即便旧键仍在也不能再次覆盖 Room。
            db.getUserPersonaDao().describe(1, "Edited description")
            failCommit = false
            manager.upgrade()
            manager.upgrade()
            assertEquals("Edited description", db.getUserPersonaDao().active().description)
            assertFalse(prefs.contains("userName"))
        }
    }

    /**
     * 为每个用例创建不读取在线应用偏好的空库，并在结束时释放资源。
     * @param block 使用隔离数据库与旧偏好的测试流程。
     */
    private suspend fun withStorage(block: suspend (AppDatabase, SharedPreferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "persona-upgrade-${UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        // 旧键只存在于隔离偏好；无关键验证清理边界。
        check(
            prefs.edit().putString("userName", "Legacy")
                .putString("userDescription", "Description").putString("unrelated", "keep")
                .putLong("activePromptPresetId", PromptTemplateSnapshot.BUILTIN_ID).commit()
        )
        try {
            block(db, prefs)
        } finally {
            db.close()
            context.deleteSharedPreferences(name)
        }
    }

    /**
     * 创建使用隔离检查点的真实升级调度器。
     * @param store 本次用例的检查点。
     * @param steps 待验证的升级步骤。
     * @return 目标版本覆盖全部步骤的调度器。
     */
    private fun manager(store: Checkpoints, steps: List<AppUpgrade>) = AppUpgradeManager(
        AppVersionCodeProvider { steps.maxOf { it.targetVersionCode } }, store, steps,
    )

    private class Checkpoints : AppUpgradeVersionStore {
        var failMigrationCheckpoint = false
        override var lastCompletedVersionCode = 20260103
            set(value) {
                check(!failMigrationCheckpoint); field = value
            }
        override var lastCleanedVersionCode = 20260103
    }
}
