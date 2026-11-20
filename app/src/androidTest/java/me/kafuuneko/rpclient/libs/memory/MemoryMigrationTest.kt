package me.kafuuneko.rpclient.libs.memory

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.migration.PersonaSchemaMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证已有 v6 摘要和会话在增加独立记忆表后保持原样。 */
@RunWith(AndroidJUnit4::class)
class MemoryMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    /** 迁移创建空记忆仓库并关闭普通召回，不迁走或改写旧摘要。 */
    @Test
    fun sixToSevenPreservesSummariesAndUsesSafeConversationDefaults() {
        val name = "memory-six-to-seven"
        // 构造包含单聊和群聊摘要的 v6 测试数据库。
        helper.createDatabase(name, 6).use { old ->
            seed(old, "user_personas", mapOf("id" to 1, "name" to "User"))
            seed(old, "user_persona_settings", mapOf("id" to 1, "defaultPersonaId" to 1))
            seed(old, "character", mapOf("id" to 1, "name" to "Character"))
            seed(old, "chat_sessions", mapOf("id" to 1, "characterId" to 1, "userPersonaId" to 1))
            seed(
                old,
                "chat_messages",
                mapOf(
                    "id" to 1,
                    "sessionId" to 1,
                    "source" to "User",
                    "content" to "original message",
                ),
            )
            seed(
                old,
                "chat_messages",
                mapOf(
                    "id" to 2,
                    "sessionId" to 1,
                    "source" to "Summary",
                    "content" to "original summary",
                    "coveredMessageId" to 1,
                ),
            )
            seed(
                old,
                "group_chat_sessions",
                mapOf(
                    "id" to 1,
                    "userPersonaId" to 1,
                    "activationStrategy" to "Manual",
                    "characterCardMode" to "Swap",
                ),
            )
            seed(
                old,
                "group_chat_summaries",
                mapOf(
                    "id" to 1,
                    "sessionId" to 1,
                    "content" to "group summary",
                    "coveredMessageId" to 0,
                ),
            )
        }
        // 运行 v6 到 v7 迁移并验证旧摘要保持原样、默认开关安全、三族记忆表为空且校验通过。
        helper.runMigrationsAndValidate(name, 7, true, PersonaSchemaMigration).use { db ->
            db.query("SELECT content FROM chat_messages WHERE id = 2").use {
                assertTrue(it.moveToFirst())
                assertEquals("original summary", it.getString(0))
            }
            db.query("SELECT content FROM group_chat_summaries WHERE id = 1").use {
                assertTrue(it.moveToFirst())
                assertEquals("group summary", it.getString(0))
            }
            // 两种会话使用同一默认策略；已有内容不会意外开始携带跨会话资料。
            listOf("chat_sessions", "group_chat_sessions").forEach { table ->
                db.query("SELECT inheritCharacterMemory, recallMemoryInNormalChat FROM $table")
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(1, it.getInt(0))
                        assertEquals(0, it.getInt(1))
                    }
            }
            listOf("character", "chat", "group_chat").forEach { family ->
                db.query("SELECT COUNT(*) FROM ${family}_memories").use {
                    it.moveToFirst()
                    assertEquals(0, it.getInt(0))
                }
            }
            MemoryRestoreValidator.validate(db)
        }
    }

    /** 旧版本必填列由其实际 schema 填入中性值，只显式指定测试关心的业务内容。 */
    private fun seed(db: SupportSQLiteDatabase, table: String, values: Map<String, Any>) {
        val row = values.toMutableMap()
        db.query("PRAGMA table_info(`$table`)").use { columns ->
            while (columns.moveToNext()) {
                val name = columns.getString(1)
                val required = columns.getInt(3) == 1 && columns.isNull(4)
                if (required && name !in row) row[name] =
                    if (columns.getString(2) == "INTEGER") 0 else ""
            }
        }
        val names = row.keys.joinToString(",") { "`$it`" }
        val slots = row.keys.joinToString(",") { "?" }
        db.execSQL("INSERT INTO `$table` ($names) VALUES ($slots)", row.values.toTypedArray())
    }
}
