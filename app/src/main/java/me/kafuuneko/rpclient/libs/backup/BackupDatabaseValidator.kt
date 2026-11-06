package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.File
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase
import me.kafuuneko.rpclient.libs.search.SearchDatabaseCallback

/**
 * 在暂存目录校验并准备可恢复的数据库。
 * - 使用应用既有迁移，禁止降级时破坏性重建。
 * - 所有完整恢复入口在结构验证后清空检索副本并关闭搜索加速。
 * - 校验失败时尚未关闭或替换当前数据库。
 */
internal object BackupDatabaseValidator {
    /**
     * 校验、迁移暂存主库，并为恢复后的手动搜索加速构建准备空状态。
     * @param context 用于创建独立 Room 实例。
     * @param file 尚未替换当前数据的暂存文件。
     * @param preferences 备份自己的偏好目录，旧提示词迁入不能读取在线偏好。
     */
    fun preparePrimaryForRestore(context: Context, file: File, preferences: File? = null) {
        validateIntegrity(file)
        // 保留迁移前版本，用于区分旧偏好导入与新版模板状态校验。
        val sourceVersion =
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                .use { it.version }
        val candidate = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .build()
        try {
            val db = candidate.openHelper.writableDatabase
            PromptPresetRestore.prepare(context, db, sourceVersion, preferences)
            SearchDatabaseCallback.resetAfterRestore(db)
        } finally {
            candidate.close()
        }
    }

    /** 校验请求日志库，拒绝当前应用无法打开的数据库版本。 */
    fun validateLogs(context: Context, file: File) {
        validateIntegrity(file)
        val candidate =
            Room.databaseBuilder(context, RequestLogDatabase::class.java, file.path).build()
        try {
            candidate.openHelper.writableDatabase
        } finally {
            candidate.close()
        }
    }

    /** 同时检查物理页和外键关系，避免将损坏的 SQLite 快照交给 Room。 */
    private fun validateIntegrity(file: File) {
        // 部分系统 SQLite 的 FTS4 integrity_check 需要可写连接；只读打开会将有效
        // 索引误报为损坏。这里只打开暂存副本，校验和后续迁移都不会触碰当前数据库。
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            .use { database ->
                require(database.version > 0) { "Backup database version is missing." }
                database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    val result = if (cursor.moveToFirst()) cursor.getString(0) else null
                    require(result == "ok") {
                        "Backup database failed integrity validation: " + result
                    }
                }
                database.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                    require(!cursor.moveToFirst()) { "Backup database contains invalid foreign keys." }
                }
            }
    }
}
