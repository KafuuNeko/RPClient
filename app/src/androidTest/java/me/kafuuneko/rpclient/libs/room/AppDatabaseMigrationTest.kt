package me.kafuuneko.rpclient.libs.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.kafuuneko.rpclient.libs.llm.model.OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.room.entity.StoryCharacter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_removesHistoricalLogsAndKeepsBusinessRows() {
        migrationHelper.createDatabase(DatabaseName, 1).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions
                ) VALUES (101, 'character', '', '[]', 'description', 'personality',
                    'scenario', '[]', '', '')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO chat_sessions (
                    id, characterId, createTime, latestTime, lorebookEntrySet, title, userNote,
                    userName, userDescription, worldInfoStateJson, autoSummaryPaused
                ) VALUES (202, 101, 1, 2, '[]', 'session', '', 'user', '', '{}', 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    sendTemperature, sendTopP, promptPostProcessingMode, isEnabled,
                    createTime, updateTime
                ) VALUES (404, 'provider', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0, 1200, 8192,
                    1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO lorebooks (
                    id, name, description, scanDepth, tokenBudget,
                    recursiveScanning, extensionsJson
                ) VALUES
                    (501, 'legacy-default', '', 2, 25, 0, '{}'),
                    (502, 'explicit-budget', '', 2, 256, 0, '{}')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_request_logs (
                    id, createTime, providerName, providerType, protocol, model, isStreaming,
                    requestJson, responseJson
                ) VALUES (303, 3, 'provider', 'Custom', 'OpenAICompatible', 'model', 0,
                    '{"prompt":"PRIVATE_SENTINEL_92f1"}',
                    '{"content":"PRIVATE_SENTINEL_92f1"}')
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DatabaseName,
            2,
            true
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
        migrated.query(
            "SELECT tokenEstimateReservePercent FROM llm_providers WHERE id = 404"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(15, cursor.getInt(0))
        }
        migrated.query(
            "SELECT id, tokenBudget FROM lorebooks ORDER BY id"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(501L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals(502L, cursor.getLong(0))
            assertEquals(256, cursor.getInt(1))
        }
    }

    @Test
    fun migrate2To3_addsCurrentStorageAndKeepsCharacters() {
        migrationHelper.createDatabase(RegexDatabaseName, 2).apply {
            execSQL(
                """
                INSERT INTO character (
                    id, name, avatar, characterTags, description, personality, scenario,
                    firstMessages, examplesOfDialogue, postHistoryInstructions, extensionsJson
                ) VALUES (
                    101, 'character', '', '[]', '', '', '', '', '', '',
                    '{"regex_scripts":[{"id":"legacy"}],"vendor":{"kept":true}}'
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (404, 'provider', 'OpenRouter', 'OpenAICompatible',
                    'https://openrouter.ai/api/v1', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO llm_providers (
                    id, name, providerType, protocol, baseUrl, apiKey, model,
                    customHeadersJson, temperature, topP, maxTokens, contextTokens,
                    tokenEstimateReservePercent, sendTemperature, sendTopP,
                    promptPostProcessingMode, isEnabled, createTime, updateTime
                ) VALUES (405, 'custom', 'Custom', 'OpenAICompatible',
                    'https://example.invalid', '', 'model', '', 0.8, 1.0,
                    1200, 8192, 15, 1, 1, 0, 1, 4, 4)
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            RegexDatabaseName,
            3,
            true
        )

        migrated.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table' AND name IN (
                'character_llm_provider_associations',
                'regex_scripts',
                'regex_character_authorizations',
                'stories',
                'story_characters',
                'story_chapters',
                'story_lorebook_entries',
                'story_volumes'
            )
            ORDER BY name
            """.trimIndent()
        ).use { cursor ->
            val tableNames = buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(
                listOf(
                    "character_llm_provider_associations",
                    "regex_character_authorizations",
                    "regex_scripts",
                    "stories",
                    "story_characters",
                    "story_chapters",
                    "story_lorebook_entries",
                    "story_volumes"
                ),
                tableNames
            )
        }
        migrated.query("SELECT extensionsJson FROM character WHERE id = 101").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(true, cursor.getString(0).contains("regex_scripts"))
        }
        migrated.query("SELECT COUNT(*) FROM character_llm_provider_associations").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        migrated.query(
            "SELECT id, requestBodyPatchJson FROM llm_providers WHERE id IN (404, 405) ORDER BY id"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(
                OPENROUTER_SESSION_AFFINITY_REQUEST_BODY_PATCH_JSON,
                cursor.getString(1)
            )
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals("{}", cursor.getString(1))
        }
        migrated.execSQL(
            """
            INSERT INTO stories (
                id, title, memory, summary, authorNote,
                worldInfoGenerationStep, revision,
                createTime, latestTime
            ) VALUES (202, 'draft', 'memory', 'summary', 'note', 3, 7, 5, 6)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO story_volumes (id, storyId, title, sortOrder)
            VALUES (303, 202, 'Volume One', 0)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO story_chapters (
                id, storyId, volumeId, title, content, sortOrder,
                contentRevision, createTime, latestTime
            ) VALUES (304, 202, 303, 'Chapter One', 'body', 0, 4, 5, 6)
            """.trimIndent()
        )
        migrated.execSQL(
            """
            INSERT INTO story_characters (
                storyId, characterId, sortOrder, activationMode
            ) VALUES (202, 101, 0, ${StoryCharacter.ACTIVATION_PRIMARY})
            """.trimIndent()
        )
        migrated.query(
            """
            SELECT title, memory, summary, authorNote,
                   worldInfoGenerationStep, revision, includeUserPersona
            FROM stories WHERE id = 202
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("draft", cursor.getString(0))
            assertEquals("memory", cursor.getString(1))
            assertEquals("summary", cursor.getString(2))
            assertEquals("note", cursor.getString(3))
            assertEquals(3, cursor.getInt(4))
            assertEquals(7L, cursor.getLong(5))
            assertEquals(0, cursor.getInt(6))
        }
        migrated.query(
            """
            SELECT volume.title, chapter.title, chapter.content, chapter.contentRevision
            FROM story_chapters AS chapter
            JOIN story_volumes AS volume ON volume.id = chapter.volumeId
            WHERE chapter.id = 304
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Volume One", cursor.getString(0))
            assertEquals("Chapter One", cursor.getString(1))
            assertEquals("body", cursor.getString(2))
            assertEquals(4L, cursor.getLong(3))
        }
        migrated.query(
            """
            SELECT characterId, activationMode
            FROM story_characters WHERE storyId = 202
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(101L, cursor.getLong(0))
            assertEquals(StoryCharacter.ACTIVATION_PRIMARY, cursor.getInt(1))
        }
    }

    @Test
    fun migrate3To4_addsTokenUsageStorageWithoutSensitivePayloadColumns() {
        migrationHelper.createDatabase(TokenUsageDatabaseName, 3).apply {
            execSQL(
                """
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
                """.trimIndent()
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TokenUsageDatabaseName,
            4,
            true
        )

        // 新表只接受脱敏统计快照，并验证迁移后的实际读写结构
        migrated.execSQL(
            """
            INSERT INTO llm_token_usage_records (
                createTime, providerId, providerName, providerType, protocol,
                apiHost, apiPort, requestedModel, effectiveModel, isStreaming,
                inputTokens, outputTokens, inputTokenSource, outputTokenSource,
                cachedInputTokens, reasoningTokens, tokenizerName, durationMs
            ) VALUES (
                100, 7, 'provider', 'Custom', 'OpenAICompatible',
                'example.invalid', 443, 'requested', 'effective', 1,
                120, 30, 'ProviderReported', 'ProxyEstimate',
                80, NULL, 'CL100K proxy', 250
            )
            """.trimIndent()
        )
        migrated.query(
            """
            SELECT providerName, apiHost, requestedModel, effectiveModel,
                   inputTokens, outputTokens, inputTokenSource, outputTokenSource
            FROM llm_token_usage_records
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("provider", cursor.getString(0))
            assertEquals("example.invalid", cursor.getString(1))
            assertEquals("requested", cursor.getString(2))
            assertEquals("effective", cursor.getString(3))
            assertEquals(120L, cursor.getLong(4))
            assertEquals(30L, cursor.getLong(5))
            assertEquals("ProviderReported", cursor.getString(6))
            assertEquals("ProxyEstimate", cursor.getString(7))
        }
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
            """
            SELECT id, useServerReportedUsage
            FROM llm_providers
            WHERE id IN (404, 405, 406)
            ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(404L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals(405L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(true, cursor.moveToNext())
            assertEquals(406L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
        }
    }

    private companion object {
        const val DatabaseName = "app-migration-test"
        const val RegexDatabaseName = "app-regex-migration-test"
        const val TokenUsageDatabaseName = "app-token-usage-migration-test"
    }
}
