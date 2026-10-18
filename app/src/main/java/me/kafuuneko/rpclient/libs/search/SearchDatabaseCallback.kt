package me.kafuuneko.rpclient.libs.search

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 安装源变更触发器，保证所有 DAO、批量导入及级联删除都留下同事务脏标记。
 * 触发器只记录代次，文字投影与 FTS 写入交给后台工作器。
 */
object SearchDatabaseCallback : RoomDatabase.Callback() {
    /** 每次打开幂等安装；结构迁移不会遍历历史正文。 */
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // 进度行须先存在，业务写入才可以原子推进全库代次。
        for (kind in 0..3) {
            db.execSQL("INSERT OR IGNORE INTO search_progress(kind, cursor, complete) VALUES($kind, 0, 0)")
        }
        db.execSQL("INSERT OR IGNORE INTO search_progress(kind, cursor, complete) VALUES(-1, 0, 1)")
        installSource(db, "chat_messages", 1, "sessionId", "content, source, sessionId", "NEW.source = 'Summary'")
        installSource(db, "group_chat_messages", 2, "sessionId", "content, speakerNameSnapshot, sessionId", "0")
        installSource(db, "story_chapters", 3, "storyId", "content, title, storyId", "0")
        // 元数据和排序字段的变更也使旧游标失效，但无需重建所有正文块。
        listOf("chat_sessions", "group_chat_sessions", "stories", "character", "group_chat_members", "story_characters")
            .forEach { table ->
                listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS search_meta_${table}_$operation AFTER $operation ON `$table`
                        BEGIN UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0; END
                    """.trimIndent())
                }
            }
    }

    private fun installSource(db: SupportSQLiteDatabase, table: String, kind: Int, parent: String, columns: String, hidden: String) {
        // INSERT OR IGNORE 加 UPDATE 兼容 API 26，不依赖新版本 UPSERT。
        listOf("INSERT", "UPDATE OF $columns", "DELETE").forEach { operation ->
            val deleting = operation == "DELETE"
            val row = if (deleting) "OLD" else "NEW"
            val deleted = if (deleting) "1" else hidden
            val suffix = operation.substringBefore(' ')
            db.execSQL("""
                CREATE TRIGGER IF NOT EXISTS search_source_${table}_$suffix AFTER $operation ON `$table`
                BEGIN
                    INSERT OR IGNORE INTO search_sources(kind, sourceId, parentId, revision, indexedRevision, deleted, dirty, changedTime)
                        VALUES($kind, $row.id, $row.$parent, 0, 0, $deleted, 1, CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER));
                    UPDATE search_sources SET parentId = $row.$parent, revision = revision + 1, deleted = $deleted, dirty = 1, changedTime = CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)
                        WHERE kind = $kind AND sourceId = $row.id;
                    UPDATE search_progress SET cursor = cursor + 1 WHERE kind = 0;
                END
            """.trimIndent())
        }
    }
}
