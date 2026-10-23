package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.llm.model.OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_removesHistoricalLogsAndKeepsBusinessRows() {
        migrationHelper.createDatabase(name = DatabaseName, version = 1).apply {
            execSQL(
                sql = """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', 'description', 'personality',
                    'scenario', '[]', '', '')
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    sendTemperature, sendTopP, promptPostProcessingMode, isEnabled,
                    createTime, updateTime
                ) VALUES (404, 'provider', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0, 1200, 8192,
                    1, 1, 0, 1, 4, 4)
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO lorebooks (
                    id, name, description, scanDepth, tokenBudget,
                    recursiveScanning, extensionsJson
                ) VALUES
                    (501, 'legacy-default', '', 2, 25, 0, '{}'),
                    (502, 'explicit-budget', '', 2, 256, 0, '{}')
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO llm_request_logs (
                    id, createTime, providerName, providerType, protocol, model, isStreaming,
                    requestJson, responseJson
                ) VALUES (303, 3, 'provider', 'Custom', 'OpenAICompatible', 'model', 0,
                    '{"prompt":"PRIVATE_SENTINEL_92f1"}',
                    '{"content":"PRIVATE_SENTINEL_92f1"}')
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            name = DatabaseName,
            version = 2,
            validateDroppedTables = true,
        )

        migrated.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            val tableNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertFalse(tableNames.contains("llm_request_logs"))
        }
        migrated.query("SELECT name FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("character", cursor.getString(0))
        }
        migrated.query("SELECT title, latestTime FROM chat_sessions WHERE id = 202").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("session", cursor.getString(0))
            assertEquals(2L, cursor.getLong(1))
        }
        migrated.query("SELECT tokenEstimateReservePercent FROM llm_providers WHERE id = 404").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
        }
        migrated.query("SELECT id, tokenBudget FROM lorebooks ORDER BY id").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(501L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals(502L, cursor.getLong(0))
            assertEquals(256, cursor.getInt(1))
        }
    }

    @Test
    fun migrate2To3_keepsCharactersAndMigratesOpenRouterPatch() {
        migrationHelper.createDatabase(name = RegexDatabaseName, version = 2).apply {
            execSQL(
                sql = """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions, extensionsJson
                ) VALUES (
                    101, 'character', '', '[]', '', '', '', '', '', '',
                    '{"regex_scripts":[{"id":"legacy"}],"vendor":{"kept":true}}'
                )
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (404, 'provider', 'OpenRouter', 'OpenAICompatible',
                    'https://openrouter.ai/api/v1', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent(),
            )
            execSQL(
                sql = """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (405, 'custom', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            name = RegexDatabaseName,
            version = 3,
            validateDroppedTables = true,
        )

        // 表结构已由 Room 校验；这里只验证旧数据保留及自定义迁移行为。
        migrated.query("SELECT extensionsJson FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.getString(0).contains("regex_scripts"))
        }
        migrated.query("SELECT COUNT(*) FROM character_llm_provider_associations").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        migrated.query(
            query = "SELECT id, requestBodyPatchJson FROM llm_providers WHERE id IN (404, 405) ORDER BY id",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(
                OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON,
                cursor.getString(1),
            )
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals("{}", cursor.getString(1))
        }
    }

    @Test
    fun migrate3To4_addsTokenUsageStorageWithoutSensitivePayloadColumns() {
        migrationHelper.createDatabase(name = TokenUsageDatabaseName, version = 3).apply {
            execSQL(
                sql = """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, requestBodyPatchJson, temperature, topP,
                    maxTokens, contextTokens, tokenEstimateReservePercent,
                    sendTemperature, sendTopP, promptPostProcessingMode,
                    isEnabled, createTime, updateTime
                ) VALUES (
                    404, 'existing-openai-compatible', 'ChatGPT', 'OpenAICompatible',
                    'https://proxy.example.invalid/v1', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 1, 0, 1, 4, 4
                ), (
                    405, 'existing-gemini', 'Gemini', 'Gemini',
                    'https://generativelanguage.googleapis.com', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 1, 0, 1, 4, 4
                ), (
                    406, 'existing-anthropic', 'Claude', 'AnthropicMessages',
                    'https://api.anthropic.com', '', 'model', '', '{}',
                    0.8, 1.0, 1200, 8192, 15, 1, 0, 0, 1, 4, 4
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            name = TokenUsageDatabaseName,
            version = 4,
            validateDroppedTables = true,
        )

        // 表结构由 Room 校验，额外约束统计表不得保存敏感载荷。
        migrated.query("PRAGMA table_info(llm_token_usage_records)").use { cursor ->
            val columnNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertFalse(columnNames.contains("requestJson"))
            assertFalse(columnNames.contains("responseJson"))
            assertFalse(columnNames.contains("apiKey"))
            assertFalse(columnNames.contains("baseUrl"))
        }
        migrated.query(
            query = """
            SELECT id, useServerReportedUsage, localTokenEstimatorType, imageInputSetting, imageTokenEstimatorType
            FROM llm_providers
            WHERE id IN (404, 405, 406)
            ORDER BY id
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
            assertEquals(true, cursor.moveToNext())
            assertEquals(406L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("Automatic", cursor.getString(2))
            assertEquals("Auto", cursor.getString(3))
            assertEquals("Automatic", cursor.getString(4))
        }

        // 验证 message_images 表及其字段在 3→4 迁移中正确生成
        migrated.query("PRAGMA table_info(message_images)").use { cursor ->
            val columnNames = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
            assertEquals(setOf("messageType", "messageId", "position", "imageUuid"), columnNames)
        }
    }

    /** 外观表升级必须保留现有文件索引，不给旧用户自动设置背景。 */
    @Test
    fun migrate4To5_preservesFilesAndStartsWithThemeBackgrounds() {
        migrationHelper.createDatabase(name = "app-background-migration-test", version = 4).apply {
            execSQL("INSERT INTO files (uuid, hash, mimeType) VALUES ('existing-avatar', 'avatar-hash', 'image/png')")
            close()
        }
        val migrated = migrationHelper.runMigrationsAndValidate(
            name = "app-background-migration-test",
            version = 5,
            validateDroppedTables = true,
        )
        migrated.query("SELECT hash FROM files WHERE uuid = 'existing-avatar'").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("avatar-hash", cursor.getString(0))
        }
        migrated.query("PRAGMA table_info(character)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "backgroundFileUuid") found = true
            }
            assertEquals(true, found)
        }
        migrated.query("SELECT COUNT(*) FROM page_backgrounds").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        // 一次 4→5 升级后即可保存无图透明度，所有附加模糊列默认关闭。
        migrated.execSQL(
            sql = """
            INSERT INTO page_backgrounds (page, fileUuid, blur, scrim, opacity, focalX, focalY)
            VALUES ('chat', NULL, 25, 0.25, 1.0, 0.5, 0.5)
        """.trimIndent(),
        )
        migrated.query("SELECT followGeneral, useCharacterBackground FROM page_backgrounds").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
        }
        migrated.query("SELECT topBarBlur, bottomBarBlur, bubbleBlur, contentBlur FROM page_backgrounds").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            repeat(times = 4) { value -> assertEquals(0, cursor.getInt(value)) }
        }
    }

    private companion object {
        const val DatabaseName = "app-migration-test"
        const val RegexDatabaseName = "app-regex-migration-test"
        const val TokenUsageDatabaseName = "app-token-usage-migration-test"
    }
}
