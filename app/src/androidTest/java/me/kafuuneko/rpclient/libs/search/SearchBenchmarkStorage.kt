package me.kafuuneko.rpclient.libs.search

import android.database.sqlite.SQLiteException
import java.io.File
import me.kafuuneko.rpclient.libs.room.AppDatabase
import org.json.JSONObject

/** 合成库的存储计量；区分文件、空闲页及表页，不读取或输出正文。 */
internal class SearchBenchmarkStorage(private val mDatabase: AppDatabase, private val mFile: File) {
    /** 返回采样瞬间的主库、WAL 与 SHM 文件长度之和，不代表未采样时刻的峰值。 */
    fun fileBytes(): Long = listOf(mFile, File(mFile.path + "-wal"), File(mFile.path + "-shm")).sumOf { it.length() }

    /** 无工作器和活跃事务时统一 checkpoint，再记录可核对的页与文件计量。 */
    fun snapshot(): JSONObject {
        val db = mDatabase.openHelper.writableDatabase
        val checkpoint = db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst(); it.getInt(0) }
        check(checkpoint == 0) { "Benchmark checkpoint is busy" }
        val pageSize = scalar("PRAGMA page_size")
        val result = JSONObject().put("databaseBytes", mFile.length()).put("walBytes", File(mFile.path + "-wal").length())
            .put("autoVacuum", scalar("PRAGMA auto_vacuum"))
            .put("shmBytes", File(mFile.path + "-shm").length()).put("pageSize", pageSize)
            .put("freeBytes", scalar("PRAGMA freelist_count") * pageSize).put("pageBytes", scalar("PRAGMA page_count") * pageSize)
            .put("sourceRows", scalar("SELECT count(*) FROM search_sources"))
            .put("deletedSourceRows", scalar("SELECT count(*) FROM search_sources WHERE deleted = 1"))
            .put("chunkRows", scalar("SELECT count(*) FROM search_chunks"))
        // Android 系统 SQLite 不保证编译 DBSTAT，缺失时明确记录不可用而不是填零。
        try {
            val tables = JSONObject()
            db.query("SELECT name, SUM(pgsize) FROM dbstat GROUP BY name").use { cursor ->
                while (cursor.moveToNext()) tables.put(cursor.getString(0), cursor.getLong(1))
            }
            result.put("tablePageBytes", tables)
        } catch (_: SQLiteException) {
            result.put("tablePageBytes", JSONObject.NULL)
        }
        return result
    }

    private fun scalar(sql: String): Long = mDatabase.openHelper.readableDatabase.query(sql).use {
        check(it.moveToFirst()) { "Missing benchmark scalar" }
        it.getLong(0)
    }
}
