package me.kafuuneko.rpclient.libs.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.BLOB_KEY
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.TABLE_ROWS_KEY
import me.kafuuneko.rpclient.libs.json.JsonResourceLimitException
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** 编解码 SQLite 结构与无损数据行，重建派生 FTS 后校验完整性。 */
internal object BackupDatabaseCodec {
    /** 导出业务结构定义，FTS 影子表由虚表定义重建。 */
    fun schemaJson(db: SupportSQLiteDatabase): JSONObject {
        val objects = JSONArray()
        // 不序列化 SQLite 自建表，避免恢复时与引擎创建的结构冲突。
        db.query(
            query = "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' " +
                "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name",
            bindArgs = emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val tableName = cursor.getString(2)
                if (name != "android_metadata" && tableName != "android_metadata"
                    && !name.startsWith("search_chunks_fts_")
                ) {
                    objects.put(
                        JSONObject()
                            .put("type", cursor.getString(0))
                            .put("name", name)
                            .put("table", tableName)
                            .put("sql", cursor.getString(3)),
                    )
                }
            }
        }
        return JSONObject().put("userVersion", db.version).put("objects", objects)
    }

    /** 列出需要保存数据行的表，不重复导出派生 FTS 数据。 */
    fun tableNames(db: SupportSQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
        db.query(
            query = "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
            bindArgs = emptyArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                // FTS 内容由 search_chunks 持久化；虚表和影子表在恢复时重建。
                if (name != "search_chunks_fts" && !name.startsWith("search_chunks_fts_")) {
                    result += name
                }
            }
        }
        return result
    }

    /** 按可选条件读取原始行，保留整数、浮点数、空值和 BLOB 类型。 */
    fun readRows(
        db: SupportSQLiteDatabase,
        table: String,
        whereColumn: String? = null,
        value: Any? = null,
    ): JSONArray {
        val suffix = if (whereColumn == null) "" else " WHERE " + quote(whereColumn) + " = ?"
        val projection = if (table == "llm_request_logs") logProjection(db) else "*"
        val sql = "SELECT " + projection + " FROM " + quote(table) + suffix
        val arguments: Array<Any?> = if (whereColumn == null) emptyArray() else arrayOf(value)
        // 所有值通过绑定参数传递，列值按 SQLite 的实际类型编码。
        val rows = db.query(sql, arguments).use { cursor ->
            JSONArray().apply {
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (index in 0 until cursor.columnCount) {
                        row.put(cursor.getColumnName(index), cursorValue(cursor, index))
                    }
                    put(row)
                }
            }
        }
        if (table == "llm_request_logs") {
            // 日志正文独立分块读取，备份格式仍保留原始 TEXT 字段，无需改变协议。
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                row.put("requestJson", readLogPayload(db, row.getLong("id"), "requestJson"))
                row.put("responseJson", readLogPayload(db, row.getLong("id"), "responseJson"))
            }
        }
        return rows
    }

    /** 动态保留日志表扩展列，仅把两个大 TEXT 列从窗口投影中移除。 */
    private fun logProjection(db: SupportSQLiteDatabase): String {
        return db.query("PRAGMA table_info(llm_request_logs)").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                columns += if (name == "requestJson" || name == "responseJson") {
                    "'' AS " + quote(name)
                } else quote(name)
            }
            columns.joinToString(", ")
        }
    }

    /** 从冻结备份库按 UTF-8 字节偏移读取正文，避免整行超过窗口容量。 */
    private fun readLogPayload(db: SupportSQLiteDatabase, id: Long, column: String): String {
        val result = ByteArrayOutputStream()
        var start = 1
        // 列名只由本方法的固定调用点提供；记录 ID 和偏移继续使用绑定参数。
        while (true) {
            val chunk = db.query(
                "SELECT SUBSTR(CAST(" + quote(column) + " AS BLOB), ?, 65536) FROM llm_request_logs WHERE id = ?",
                arrayOf<Any>(start, id),
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Request log disappeared from backup snapshot." }
                cursor.getBlob(0)
            }
            if (result.size().toLong() + chunk.size > 16_777_216) throw JsonResourceLimitException()
            result.write(chunk)
            if (chunk.size < 65_536) return result.toString(Charsets.UTF_8.name())
            start += 65_536
        }
    }

    /** 封装协议中的表名与行数组映射。 */
    fun tableRows(vararg tables: Pair<String, JSONArray>): JSONObject {
        val result = JSONObject()
        tables.forEach { (name, rows) -> result.put(name, rows) }
        return result
    }

    /** 收集功能文档携带的无损行；不把私有附件中的 JSON 当作业务记录。 */
    fun collectTableRows(
        root: File,
        includePath: String? = null,
        excludePath: String? = null,
    ): Map<String, List<JSONObject>> {
        val result = linkedMapOf<String, MutableList<JSONObject>>()
        fun collect(document: JSONObject) {
            val tableRows = document.optJSONObject(TABLE_ROWS_KEY) ?: return
            val keys = tableRows.keys()
            while (keys.hasNext()) {
                val table = keys.next()
                val rows = tableRows.getJSONArray(table)
                val target = result.getOrPut(table) { mutableListOf() }
                rows.objects().forEach { target.add(it) }
            }
        }
        // 业务文档解析失败必须终止恢复，不能跳过记录后替换当前数据。
        root.walkTopDown()
            .filter { it.isFile && (it.extension == "json" || it.extension == "jsonl") }
            .forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (includePath != null && path != includePath) return@forEach
                if (excludePath != null && path == excludePath) return@forEach
                if (path.startsWith("app-data/private-files/")) return@forEach
                if (file.extension == "json") {
                    collect(JSONObject(file.readText(Charsets.UTF_8)))
                } else {
                    file.forEachLine(Charsets.UTF_8) { line ->
                        if (line.isNotBlank()) collect(JSONObject(line))
                    }
                }
            }
        return result.mapValues { it.value.toList() }
    }

    /** 根据旧版 schema 将安全文件名映射回原表名。 */
    fun readLegacyRows(snapshot: File): Map<String, List<JSONObject>> {
        val schema = JSONObject(File(snapshot, "schema.json").readText(Charsets.UTF_8))
        val objects = schema.optJSONArray("objects") ?: JSONArray()
        val result = linkedMapOf<String, MutableList<JSONObject>>()
        File(snapshot, "tables").listFiles().orEmpty()
            .filter { it.isFile && it.extension == "jsonl" }.forEach { file ->
                val table = tableNameForFile(objects, file.nameWithoutExtension)
                if (table == "android_metadata") return@forEach
                file.forEachLine(Charsets.UTF_8) { line ->
                    if (line.isNotBlank()) result.getOrPut(table) { mutableListOf() }
                        .add(JSONObject(line))
                }
            }
        return result.mapValues { it.value.toList() }
    }

    /** 重建暂存数据库，导入原始行后恢复索引、触发器并验证外键。 */
    fun reconstructDatabase(
        schema: JSONObject,
        tableRows: Map<String, List<JSONObject>>,
        destination: File,
    ) {
        val objects = schema.optJSONArray("objects") ?: error("Database schema is invalid.")
        objects.objects().forEach { item ->
            val type = item.getString("type")
            val prefix = when (type) {
                "table" -> Regex("(?is)^CREATE\\s+(?:VIRTUAL\\s+)?TABLE\\s+")
                "index" -> Regex("(?is)^CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+")
                "trigger" -> Regex("(?is)^CREATE\\s+TRIGGER\\s+")
                "view" -> Regex("(?is)^CREATE\\s+VIEW\\s+")
                else -> error("Unsupported schema object type")
            }
            require(
                prefix.containsMatchIn(
                    item.getString("sql").trim()
                )
            ) { "Invalid schema definition" }
        }
        val allowedTables = mutableSetOf<String>()
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("type") == "table" && item.optString("name") != "android_metadata") {
                allowedTables.add(item.optString("name"))
            }
        }
        val unknownTable = tableRows.keys.firstOrNull { it !in allowedTables }
        require(unknownTable == null) { "Backup contains rows for an unknown table: " + unknownTable }
        destination.parentFile?.mkdirs()
        if (destination.exists()) destination.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(destination, null)
        try {
            db.execSQL("PRAGMA foreign_keys=OFF")
            for (index in 0 until objects.length()) {
                val item = objects.getJSONObject(index)
                if (item.optString("type") != "table") continue
                val name = item.optString("name")
                // android_metadata 由 Android 打开数据库时创建，不从归档重复创建。
                if (name != "android_metadata" && !name.startsWith("search_chunks_fts_")
                    && !sqliteTableExists(db, name)
                ) {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.beginTransaction()
            try {
                tableRows.forEach { (table, rows) ->
                    if (table != "search_chunks_fts" && !table.startsWith("search_chunks_fts_")) {
                        insertRows(db, table, rows)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            // 在业务行和 rowid 恢复后重建倒排索引，保留搜索进度与用户开关。
            if (sqliteTableExists(db, "search_chunks_fts")) {
                db.execSQL("INSERT INTO search_chunks_fts(search_chunks_fts) VALUES('rebuild')")
            }
            for (index in 0 until objects.length()) {
                val item = objects.getJSONObject(index)
                if (item.optString("type") != "table" && item.optString("table") != "android_metadata") {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.version = schema.optInt("userVersion", 0)
            db.execSQL("PRAGMA foreign_keys=ON")
            db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                    "Reconstructed database failed SQLite integrity validation."
                }
            }
            db.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                require(!cursor.moveToFirst()) {
                    "Reconstructed database contains invalid foreign-key references."
                }
            }
        } finally {
            db.close()
        }
    }

    /** 保留每列的 SQLite 类型，通过绑定参数写入业务行。 */
    private fun insertRows(db: SQLiteDatabase, table: String, rows: List<JSONObject>) {
        // 列名来自归档，必须引用为标识符；值统一绑定，不能拼入 SQL。
        rows.forEach { row ->
            val columns = mutableListOf<String>()
            val values = mutableListOf<Any?>()
            val keys = row.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                columns.add(key)
                values.add(jsonValue(row.get(key)))
            }
            if (columns.isNotEmpty()) {
                val sql = "INSERT INTO " + quote(table) + " (" +
                    columns.joinToString(",") { text -> quote(text) } + ") VALUES (" +
                    columns.joinToString(",") { "?" } + ")"
                val statement = db.compileStatement(sql)
                try {
                    values.forEachIndexed { index, value -> bind(statement, index + 1, value) }
                    statement.executeInsert()
                } finally {
                    statement.close()
                }
            }
        }
    }

    private fun tableNameForFile(objects: JSONArray, safeName: String): String {
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("type") == "table" && safeTableName(item.optString("name")) == safeName) {
                return item.optString("name")
            }
        }
        error("Archive references an unknown database table: " + safeName)
    }

    private fun sqliteTableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { cursor ->
                return cursor.moveToFirst()
            }
    }

    private fun cursorValue(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(
            BLOB_KEY,
            Base64.encodeToString(
                cursor.getBlob(index),
                Base64.NO_WRAP,
            ),
        )

        else -> JSONObject.NULL
    }

    private fun jsonValue(value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return null
        if (value is JSONObject && value.has(BLOB_KEY)) return Base64.decode(
            value.getString(
                BLOB_KEY
            ), Base64.DEFAULT
        )
        return value
    }

    private fun bind(statement: SQLiteStatement, index: Int, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> statement.bindNull(index)
            is ByteArray -> statement.bindBlob(index, value)
            is Boolean -> statement.bindLong(index, if (value) 1 else 0)
            is Int -> statement.bindLong(index, value.toLong())
            is Long -> statement.bindLong(index, value)
            is Double -> statement.bindDouble(index, value)
            is Float -> statement.bindDouble(index, value.toDouble())
            else -> statement.bindString(index, value.toString())
        }
    }

    private fun safeTableName(name: String) = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    private fun quote(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
}
