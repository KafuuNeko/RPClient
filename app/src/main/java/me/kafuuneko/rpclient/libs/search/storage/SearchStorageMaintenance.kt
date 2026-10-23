package me.kafuuneko.rpclient.libs.search.storage

import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.room.AppDatabase
import java.io.File

/** 搜索关闭后的文件回收，仅由索引生命周期管理器在工作器退出后调用。 */
internal class SearchStorageMaintenance(private val mDatabase: AppDatabase) {
    /** 按实际 auto_vacuum 选择回收；失败保留待清理状态，不谎报已经释放磁盘。 */
    fun reclaim() {
        val db = mDatabase.openHelper.writableDatabase
        val path = db.path ?: return
        if (path == ":memory:") return
        checkpoint(db)
        val freePages = scalar(db, "PRAGMA freelist_count")
        if (freePages > 0) {
            when (scalar(db, "PRAGMA auto_vacuum")) {
                2L -> db.execSQL("PRAGMA incremental_vacuum")
                0L -> {
                    // NONE 模式需要重写主库；必须预留 SQLite 文档要求的临时空间。
                    val bytes = scalar(db, "PRAGMA page_count") * scalar(db, "PRAGMA page_size")
                    val available = File(path).parentFile?.usableSpace ?: 0
                    check(available >= bytes * 2 + 64L * 1024 * 1024) { "Insufficient storage to reclaim search index" }
                    db.execSQL("VACUUM")
                }
            }
        }
        checkpoint(db)
    }

    /** WAL 仍被占用时可重试，不能把未落盘的文件长度当作已回收。 */
    private fun checkpoint(db: SupportSQLiteDatabase) {
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Search cleanup checkpoint is busy" }
        }
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use { cursor ->
        check(cursor.moveToFirst()) { "Missing storage pragma" }
        cursor.getLong(0)
    }
}
