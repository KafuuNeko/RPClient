package me.kafuuneko.rpclient.libs.upgrade.steps

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.upgrade.LegacyPromptSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 验证跨存储提交顺序：数据库失败保留旧值，清理重试只删除迁移拥有的键。 */
@RunWith(AndroidJUnit4::class)
class Upgrade20260404Test {
    @Test
    fun failedMigrationPreservesPreferencesAndCleanupDoesNotReplayDeletedTemplate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = AppModel.preferences
        val managedKeys = LegacyPromptSnapshot.keys + setOf(
            "promptMigrationUnrelatedTest",
            "activePromptPresetId"
        )
        val previous = preferences.all.filterKeys { it in managedKeys }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // 为失败注入只修改测试负责的键，结束时完整恢复原来的值。
            preferences.edit().apply {
                managedKeys.forEach(::remove)
                putString("mainPrompt", "\n  old {{char}}  \n")
                putString("promptMigrationUnrelatedTest", "keep")
            }.commit()
            val db = database.openHelper.writableDatabase
            db.execSQL("CREATE TRIGGER fail_prompt BEFORE INSERT ON prompt_preset_entries BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END")
            val step = Upgrade20260404(database, context)
            assertTrue(runCatching { step.migrate() }.isFailure)
            assertEquals("\n  old {{char}}  \n", preferences.getString("mainPrompt", null))
            assertTrue(runCatching { step.cleanup() }.isFailure)
            assertTrue(preferences.contains("mainPrompt"))

            // 数据提交之后的重复清理不能删除其他偏好，也不能复活已删除模板。
            db.execSQL("DROP TRIGGER fail_prompt")
            step.migrate()
            assertTrue(preferences.contains("mainPrompt"))
            val repository = PromptPresetRepository(database)
            assertEquals("\n  old {{char}}  \n", repository.getActiveSnapshot().mainPrompt)
            assertEquals(
                context.getString(me.kafuuneko.rpclient.R.string.prompt_template_migrated),
                repository.getDetail(1).preset.name
            )
            repository.delete(PromptTemplateSnapshot.LEGACY_ID)
            step.migrate()
            step.cleanup()
            step.cleanup()
            LegacyPromptSnapshot.keys.forEach { assertFalse(preferences.contains(it)) }
            assertEquals("keep", preferences.getString("promptMigrationUnrelatedTest", null))
            assertEquals(PromptTemplateSnapshot.BUILTIN_ID, repository.getActiveSnapshot().presetId)
        } finally {
            database.close()
            preferences.edit().apply {
                managedKeys.forEach(::remove)
                previous.forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Int -> putInt(key, value)
                        is Boolean -> putBoolean(key, value)
                        is Long -> putLong(key, value)
                        is Float -> putFloat(key, value)
                    }
                }
            }.commit()
        }
    }
}
