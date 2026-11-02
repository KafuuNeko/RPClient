package me.kafuuneko.rpclient.libs.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.File
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.RequestLogDatabase

/**
 * 在暂存目录验证数据库的可打开性。
 * - 使用应用既有迁移，禁止降级时破坏性重建。
 * - 校验失败时尚未关闭或替换当前数据库。
 */
internal object BackupDatabaseValidator {
    /** 校验主库完整性、Room 标识和迁移兼容性，迁移只写入暂存副本。 */
    fun validatePrimary(context: Context, file: File) {
        validateIntegrity(file)
        val candidate = Room.databaseBuilder(context, AppDatabase::class.java, file.path)
            .build()
        try {
            candidate.openHelper.writableDatabase
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
