package me.kafuuneko.rpclient.libs.upgrade

import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.defaults.normalizedUserName
import me.kafuuneko.rpclient.libs.room.AppDatabase

/**
 * 在线启动与备份恢复共用的人设迁入逻辑。
 * - 默认指针标记数据库事务已提交，重试时保留后续编辑。
 * - 调用方分别提供在线偏好或归档偏好。
 */
internal object UserPersonaUpgrade {
    val keys = setOf("userName", "userDescription", "userAvatar")

    /**
     * 完成数据库提交后才清除旧键，崩溃后各阶段可独立续跑。
     * @param database 已完成结构升级的在线数据库。
     */
    fun prepareLive(database: AppDatabase) {
        val prefs = AppModel.preferences
        val db = database.openHelper.writableDatabase
        if (!initialized(db)) migrate(db, prefs.all)
        if (keys.any(prefs::contains)) {
            val editor = prefs.edit()
            keys.forEach(editor::remove)
            check(editor.commit()) { "Failed to clean legacy persona preferences" }
        }
    }

    /**
     * 在线启动和归档恢复分别传入各自的偏好。
     * @param db 已升级到当前结构的数据库。
     * @param values 对应数据来源的旧偏好快照。
     */
    fun migrate(db: SupportSQLiteDatabase, values: Map<String, *>) {
        db.beginTransaction()
        try {
            if (!initialized(db)) importLegacy(db, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun initialized(db: SupportSQLiteDatabase) =
        db.query("SELECT 1 FROM user_persona_settings WHERE id=1").use { it.moveToFirst() }

    /**
     * 校验恢复后的人设引用，故事的禁用值采用业务约束。
     * @param db 尚未替换在线数据的暂存数据库。
     */
    fun validate(db: SupportSQLiteDatabase) {
        val invalid = listOf(
            "SELECT 1 WHERE (SELECT count(*) FROM user_persona_settings) != 1",
            "SELECT 1 FROM user_persona_settings WHERE id != 1 OR defaultPersonaId NOT IN (SELECT id FROM user_personas)",
            "SELECT 1 FROM user_personas WHERE id <= 0",
        ) + listOf("chat_sessions", "group_chat_sessions", "stories").map { table ->
            "SELECT 1 FROM $table WHERE userPersonaId != -1 AND userPersonaId NOT IN (SELECT id FROM user_personas)"
        }
        invalid.forEach { query ->
            db.query(query)
                .use { check(!it.moveToFirst()) { "Invalid backup user persona binding" } }
        }
    }

    /** 固定旧会话的有效名称，只对描述恢复继承，保留原有发言身份。 */
    private fun importLegacy(db: SupportSQLiteDatabase, values: Map<String, *>) {
        fun text(key: String): String =
            if (key in values) requireNotNull(values[key] as? String) else ""

        val name = text("userName").normalizedUserName()
        val description = text("userDescription")
        val avatar = text("userAvatar")
        db.query("SELECT 1 FROM user_personas WHERE id != 1")
            .use { check(!it.moveToFirst()) { "Missing default persona" } }

        // 空库和已完成结构升级的库共用固定迁入 ID。
        db.execSQL(
            "INSERT OR IGNORE INTO user_personas(id,name,description) VALUES(1,?,?)",
            arrayOf(name, description)
        )
        db.execSQL(
            "UPDATE user_personas SET name=?, description=?, avatarFileUuid=(SELECT uuid FROM files WHERE uuid=?) WHERE id=1",
            arrayOf(name, description, avatar)
        )
        for (table in listOf("chat_sessions", "group_chat_sessions")) {
            db.execSQL(
                "UPDATE $table SET userName=COALESCE(NULLIF(userName,''),?), userDescription=NULLIF(userDescription,?) WHERE userPersonaId=1",
                arrayOf(name, description)
            )
        }
        db.execSQL("INSERT INTO user_persona_settings(id,defaultPersonaId) VALUES(1,1)")
    }
}
