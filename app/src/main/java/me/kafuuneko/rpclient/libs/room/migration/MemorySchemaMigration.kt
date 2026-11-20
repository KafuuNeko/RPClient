package me.kafuuneko.rpclient.libs.room.migration

import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/** v6 → v7 由 Room 创建空记忆表，原摘要保持原样。 */
class MemorySchemaMigration : AutoMigrationSpec {
    /** 结构迁移后补上 Room 注解不能表达的跨字段约束。 */
    override fun onPostMigrate(db: SupportSQLiteDatabase) = MemoryDatabaseConstraints.install(db)
}

/** 新装、迁移和恢复共用的记忆身份约束安装器。 */
object MemoryDatabaseConstraints : RoomDatabase.Callback() {
    /** 新建库在任何记忆写入之前安装一致性约束。 */
    override fun onCreate(db: SupportSQLiteDatabase) = install(db)

    /** 已恢复数据库同样必须具备跨字段约束。 */
    override fun onOpen(db: SupportSQLiteDatabase) = install(db)

    /**
     * 安装规范 slotKey 与单聊私有角色归属校验。
     *
     * @param db 已具备三族表的主库或恢复暂存库
     */
    fun install(db: SupportSQLiteDatabase) {
        // 表名均来自固定白名单，不接收包文件或用户文本作为 SQL 标识符。
        listOf("character", "chat", "group_chat").forEach { family ->
            val privateId =
                if (family == "character") "0" else "COALESCE(NEW.privateCharacterId, 0)"
            val invalidOwner = if (family == "chat") {
                " OR (NEW.privateCharacterId IS NOT NULL AND NEW.privateCharacterId != (SELECT characterId FROM chat_sessions WHERE id = NEW.sessionId))"
            } else {
                ""
            }
            // 为插入与更新分别安装幂等触发器，防止绕过仓库破坏槽位键或跨角色归属。
            listOf("INSERT", "UPDATE").forEach { action ->
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS memory_${family}_${action.lowercase()}
                    BEFORE $action ON ${family}_memories
                    WHEN NEW.slotKey != CAST(COALESCE(NEW.personaId, 0) AS TEXT) || '/' || CAST($privateId AS TEXT)$invalidOwner
                    BEGIN SELECT RAISE(ABORT, 'Invalid memory identity'); END
                """.trimIndent(),
                )
            }
        }
    }
}
