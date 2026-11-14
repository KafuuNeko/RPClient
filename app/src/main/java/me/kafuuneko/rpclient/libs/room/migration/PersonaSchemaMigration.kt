package me.kafuuneko.rpclient.libs.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback

/** 在 v5 → v6 事务内保留提示词和 Claude 用量变换，再迁入会话人设绑定。 */
object PersonaSchemaMigration : Migration(5, 6) {
    private val mChildren = listOf(
        "chat_messages", "group_chat_members", "group_chat_messages", "group_chat_summaries",
        "story_volumes", "story_chapters", "story_characters", "story_lorebook_entries",
    )

    /**
     * 保留 v5 迁移的提示词与用量统计，再重建会话和故事的绑定。
     * @param db 正在执行结构迁移的数据库。
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        // 创建 v6 提示词预设表，再迁入缓存统计和人设绑定。
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `prompt_presets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL DEFAULT '',
                `revision` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `prompt_preset_entries` (
                `presetId` INTEGER NOT NULL,
                `promptKey` TEXT NOT NULL,
                `content` TEXT NOT NULL,
                PRIMARY KEY(`presetId`, `promptKey`),
                FOREIGN KEY(`presetId`) REFERENCES `prompt_presets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE llm_token_usage_records ADD COLUMN cacheCreationInputTokens INTEGER DEFAULT NULL")
        AppDatabaseAutoMigration5To6Spec().onPostMigrate(db)
        rebuild(db)
    }

    /**
     * 在迁移事务内暂存子行，防止重建父表触发级联删除。
     * @param db 正在执行结构迁移的数据库。
     */
    private fun rebuild(db: SupportSQLiteDatabase) {
        // 重建父表前卸载搜索触发器，并按外键依赖顺序暂存子行。
        SearchDatabaseCallback.uninstall(db)
        val enforceKeys =
            db.query("PRAGMA foreign_keys").use { it.moveToFirst() && it.getInt(0) != 0 }
        if (enforceKeys) {
            mChildren.forEach { table -> db.execSQL("CREATE TEMP TABLE saved_$table AS SELECT * FROM $table") }
            mChildren.asReversed().forEach { table -> db.execSQL("DELETE FROM $table") }
        }

        createPersonaTables(db)
        rebuildChats(db)
        rebuildGroups(db)
        rebuildStories(db)

        // 恢复卷、章节等依赖关系后再次验证整个库的外键。
        if (enforceKeys) mChildren.forEach { table ->
            db.execSQL("INSERT INTO $table SELECT * FROM saved_$table")
            db.execSQL("DROP TABLE saved_$table")
        }
        db.query("PRAGMA foreign_key_check")
            .use { check(!it.moveToFirst()) { "Invalid migrated foreign key" } }
    }

    /**
     * 建立人设库和默认指针表，以固定 ID 接收原有会话的绑定。
     * @param db 正在执行结构迁移的数据库。
     */
    private fun createPersonaTables(db: SupportSQLiteDatabase) {
        // 人设头像沿用文件索引，限制删除仍被引用的文件。
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_personas` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `avatarFileUuid` TEXT,
                FOREIGN KEY(`avatarFileUuid`) REFERENCES `files`(`uuid`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_personas_avatarFileUuid` ON `user_personas` (`avatarFileUuid`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_persona_settings` (
                `id` INTEGER NOT NULL,
                `defaultPersonaId` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`defaultPersonaId`) REFERENCES `user_personas`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_persona_settings_defaultPersonaId` ON `user_persona_settings` (`defaultPersonaId`)")
        // 只建占位身份；默认指针留给跨存储升级作为成功提交标记。
        db.execSQL("INSERT INTO user_personas(id,name,description) VALUES(1,'You','')")
    }

    /**
     * 保留角色外键，使单聊名称和描述可分别继承。
     * @param db 正在执行结构迁移的数据库。
     */
    private fun rebuildChats(db: SupportSQLiteDatabase) {
        // 保留会话主键和角色外键，将名称与描述改为可继承字段。
        rebuildParent(
            db = db,
            table = "chat_sessions",
            createSql = """
            CREATE TABLE IF NOT EXISTS `new_chat_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `characterId` INTEGER NOT NULL,
                `createTime` INTEGER NOT NULL,
                `latestTime` INTEGER NOT NULL,
                `lorebookEntrySet` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `userNote` TEXT NOT NULL,
                `userName` TEXT,
                `userDescription` TEXT,
                `creatorNotes` TEXT,
                `worldInfoStateJson` TEXT NOT NULL,
                `autoSummaryPaused` INTEGER NOT NULL,
                `includeCharacterExamples` INTEGER NOT NULL DEFAULT 1,
                `userPersonaId` INTEGER NOT NULL,
                FOREIGN KEY(`characterId`) REFERENCES `character`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_characterId` ON `chat_sessions` (`characterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_sessions_userPersonaId` ON `chat_sessions` (`userPersonaId`)")
    }

    /**
     * 复制群聊策略和设置，按绑定 ID 建立继承关系。
     * @param db 正在执行结构迁移的数据库。
     */
    private fun rebuildGroups(db: SupportSQLiteDatabase) {
        // 保留群聊策略原值，只替换人设绑定与覆盖列的结构。
        rebuildParent(
            db = db,
            table = "group_chat_sessions",
            createSql = """
            CREATE TABLE IF NOT EXISTS `new_group_chat_sessions` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `createTime` INTEGER NOT NULL,
                `latestTime` INTEGER NOT NULL,
                `userName` TEXT,
                `userDescription` TEXT,
                `scenario` TEXT NOT NULL,
                `userNote` TEXT NOT NULL,
                `lorebookEntrySet` TEXT NOT NULL,
                `worldInfoStateJson` TEXT NOT NULL,
                `systemPromptOverride` TEXT NOT NULL,
                `groupNudgePromptOverride` TEXT NOT NULL,
                `newGroupChatPromptOverride` TEXT NOT NULL,
                `activationStrategy` TEXT NOT NULL,
                `allowSelfResponses` INTEGER NOT NULL,
                `characterCardMode` TEXT NOT NULL,
                `includeMutedCards` INTEGER NOT NULL,
                `autoModeEnabled` INTEGER NOT NULL,
                `trimOtherSpeakers` INTEGER NOT NULL,
                `autoSummaryPaused` INTEGER NOT NULL,
                `includeCharacterExamples` INTEGER NOT NULL DEFAULT 1,
                `userPersonaId` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_chat_sessions_userPersonaId` ON `group_chat_sessions` (`userPersonaId`)")
    }

    /**
     * 用故事的绑定 ID 取代旧布尔开关。
     * @param db 正在执行结构迁移的数据库。
     */
    private fun rebuildStories(db: SupportSQLiteDatabase) {
        // 故事禁用值保留为 -1，由复制投影转换旧布尔开关。
        rebuildParent(
            db = db,
            table = "stories",
            createSql = """
            CREATE TABLE IF NOT EXISTS `new_stories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `memory` TEXT NOT NULL,
                `summary` TEXT NOT NULL,
                `authorNote` TEXT NOT NULL,
                `userPersonaId` INTEGER NOT NULL DEFAULT -1,
                `worldInfoGenerationStep` INTEGER NOT NULL,
                `revision` INTEGER NOT NULL,
                `createTime` INTEGER NOT NULL,
                `latestTime` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stories_userPersonaId` ON `stories` (`userPersonaId`)")
    }

    /**
     * 用显式列投影保留旧主键、宏原文和已删除记录的序列高水位。
     * @param db 正在执行结构迁移的数据库。
     * @param table 待重建的已知业务表名。
     * @param createSql 最终结构对应的临时新表建表语句。
     */
    private fun rebuildParent(db: SupportSQLiteDatabase, table: String, createSql: String) {
        // 保存自增高水位，避免重建后复用已删除记录的 ID。
        val sequence =
            db.query("SELECT seq FROM sqlite_sequence WHERE name = ?", arrayOf(table)).use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
        db.execSQL(createSql)
        val target = columns(db, "new_$table")
        val old = columns(db, table)
        val legacyColumns = (target - "userPersonaId").toMutableSet()
        if (table == "stories") legacyColumns.add("includeUserPersona")
        check(old.toSet() == legacyColumns) { "Unexpected columns in $table" }
        // 严格校验旧列集合，再为新增人设绑定生成兼容投影。
        val select = target.map { column ->
            when {
                column != "userPersonaId" -> "`$column`"
                table == "stories" -> "CASE includeUserPersona WHEN 0 THEN -1 WHEN 1 THEN 1 END"
                else -> "1"
            }
        }
        // 删除旧表前复制数据，新表使用最终实体的约束。
        db.execSQL("INSERT INTO new_$table (${target.joinToString { "`$it`" }}) SELECT ${select.joinToString()} FROM $table")
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE new_$table RENAME TO $table")
        if (sequence != null) {
            db.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf(table))
            db.execSQL(
                "INSERT INTO sqlite_sequence(name, seq) VALUES(?, ?)",
                arrayOf<Any>(table, sequence)
            )
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            "name"
                        )
                    )
                )
            }
        }
}
