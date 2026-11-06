package me.kafuuneko.rpclient.libs.upgrade

import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot

/**
 * 启动升级与旧备份暂存共用的预设迁入事务。
 * - 仅插入旧用户配置和差异项，默认配置不入库。
 * - 固定首次导入 ID，数据库提交后 SP 写入前中断也可重试。
 */
internal object PromptPresetMigration {
    /** 返回需要保存到 SP 的选择，调用方成功写入后才推进升级检查点。 */
    fun migrate(
        db: SupportSQLiteDatabase,
        legacy: () -> LegacyPromptSnapshot,
        migratedName: String
    ): Long {
        db.beginTransaction()
        try {
            // 已提交的迁入记录直接复用，不能覆盖其中已保存的正文。
            db.query("SELECT id FROM prompt_presets ORDER BY id LIMIT 1").use {
                if (it.moveToFirst()) {
                    val id = it.getLong(0)
                    db.setTransactionSuccessful()
                    return id
                }
            }
            val snapshot = legacy()
            if (!snapshot.hasSavedValues) {
                db.setTransactionSuccessful()
                return PromptTemplateSnapshot.BUILTIN_ID
            }
            val id = PromptTemplateSnapshot.LEGACY_ID
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT INTO prompt_presets (id, name, description, revision, createdAt, updatedAt) VALUES (?, ?, '', 1, ?, ?)",
                arrayOf(id, migratedName, now, now),
            )
            // 正文与元数据一次提交，空串只在它区别于默认值时作为覆盖保存。
            snapshot.values.forEach { (key, content) ->
                db.execSQL(
                    "INSERT INTO prompt_preset_entries (presetId, promptKey, content) VALUES (?, ?, ?)",
                    arrayOf(id, key, content)
                )
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }
}
