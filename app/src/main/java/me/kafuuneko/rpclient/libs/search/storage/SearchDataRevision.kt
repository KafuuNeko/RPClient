package me.kafuuneko.rpclient.libs.search.storage

import androidx.room.withTransaction
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.search.SearchSql

/**
 * 普通扫描的连接内版本，不使用 FTS 状态或持久待办。
 * - TEMP 计数器只有一行，随主写连接关闭而消失；版本变化与业务写入同事务提交。
 * - 不依赖 Room 异步失效通知，直接 SQL、级联删除及回滚都具有一致语义。
 */
internal class SearchDataRevision(private val mDatabase: AppDatabase) {
    /** 在短事务中读取主写连接的临时版本，正文扫描始终在事务外进行。 */
    suspend fun current(): Long = mDatabase.withTransaction {
        val db = mDatabase.openHelper.writableDatabase
        val installed = db.query("SELECT 1 FROM sqlite_temp_master WHERE type = 'table' AND name = 'search_scan_revision'").use { it.moveToFirst() }
        if (!installed) {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS search_scan_revision(value INTEGER NOT NULL)")
            db.execSQL("INSERT INTO search_scan_revision SELECT 0 WHERE NOT EXISTS(SELECT 1 FROM search_scan_revision)")
            // 普通搜索只需知道业务数据发生变化，不把临时版本写进主库或 WAL。
            for (table in SearchSql.observedTables) {
                for (operation in listOf("INSERT", "UPDATE", "DELETE")) {
                    db.execSQL("""
                        CREATE TEMP TRIGGER IF NOT EXISTS search_scan_${table}_$operation AFTER $operation ON main.`$table`
                        BEGIN UPDATE search_scan_revision SET value = value + 1; END
                    """.trimIndent())
                }
            }
        }
        db.query("SELECT value FROM search_scan_revision").use {
            check(it.moveToFirst()) { "Missing scan revision" }
            it.getLong(0)
        }
    }
}
