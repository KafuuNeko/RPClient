package me.kafuuneko.rpclient.libs.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.BLOB_KEY
import me.kafuuneko.rpclient.libs.backup.BackupArchiveFormat.TABLE_ROWS_KEY
import org.json.JSONArray
import org.json.JSONObject

/** 编解码 SQLite 结构与无损数据行，重建派生 FTS 后校验完整性。 */
internal object BackupDatabaseCodec {
    private const val MAX_PRUNE_ROUNDS = 8

    /** 导出业务结构定义，FTS 影子表由虚表定义重建。 */
    fun schemaJson(db: SupportSQLiteDatabase): JSONObject {
        val objects = JSONArray()
        // 不序列化 SQLite 自建表，避免恢复时与引擎创建的结构冲突。
        db.query(
            "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') " +
                "AND tbl_name != 'room_master_table' " +
                "ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, name", emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val tableName = cursor.getString(2)
                if (name != "android_metadata" && tableName != "android_metadata" &&
                    !name.startsWith("search_chunks_fts_")
                ) {
                    objects.put(JSONObject()
                        .put("type", cursor.getString(0))
                        .put("name", name)
                        .put("table", tableName)
                        .put("sql", cursor.getString(3)))
                }
            }
        }
        return JSONObject().put("userVersion", db.version).put("objects", objects)
    }

    /** 列出需要保存数据行的表，不重复导出派生 FTS 数据。 */
    fun tableNames(db: SupportSQLiteDatabase): List<String> {
        val result = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' " +
                "AND name NOT IN ('android_metadata', 'room_master_table') ORDER BY name", emptyArray()
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
        value: Any? = null
    ): JSONArray {
        val suffix = if (whereColumn == null) "" else " WHERE " + quote(whereColumn) + " = ?"
        val sql = "SELECT * FROM " + quote(table) + suffix
        val arguments: Array<Any?> = if (whereColumn == null) emptyArray() else arrayOf(value)
        // 所有值通过绑定参数传递，列值按 SQLite 的实际类型编码。
        return db.query(sql, arguments).use { cursor ->
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
    }

    /** 逐行流式读取原始行，避免大表一次性进入堆内存。 */
    fun forEachRow(db: SupportSQLiteDatabase, table: String, action: (JSONObject) -> Unit) {
        db.query("SELECT * FROM " + quote(table), emptyArray()).use { cursor ->
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (index in 0 until cursor.columnCount) {
                    row.put(cursor.getColumnName(index), cursorValue(cursor, index))
                }
                action(row)
            }
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
        excludePath: String? = null
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
        root.walkTopDown().filter { it.isFile && (it.extension == "json" || it.extension == "jsonl") }
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
        File(snapshot, "tables").listFiles().orEmpty().filter { it.isFile && it.extension == "jsonl" }.forEach { file ->
            val table = tableNameForFile(objects, file.nameWithoutExtension)
            if (table == "android_metadata" || table == "room_master_table") return@forEach
            file.forEachLine(Charsets.UTF_8) { line ->
                if (line.isNotBlank()) result.getOrPut(table) { mutableListOf() }.add(JSONObject(line))
            }
        }
        return result.mapValues { it.value.toList() }
    }

    /** 重建暂存数据库，导入原始行后恢复索引、触发器并验证外键。 */
    fun reconstructDatabase(
        schema: JSONObject,
        tableRows: Map<String, List<JSONObject>>,
        destination: File,
        onPruned: ((String) -> Unit)? = null
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
            require(prefix.containsMatchIn(item.getString("sql").trim())) { "Invalid schema definition" }
        }
        val allowedTables = mutableSetOf<String>()
        for (index in 0 until objects.length()) {
            val item = objects.getJSONObject(index)
            if (item.optString("type") == "table" && item.optString("name") != "android_metadata" &&
                item.optString("name") != "room_master_table") {
                allowedTables.add(item.optString("name"))
            }
        }
        val unknownTable = tableRows.keys.firstOrNull {
            it != "room_master_table" && it !in allowedTables
        }
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
                if (name != "android_metadata" && name != "room_master_table" &&
                    !name.startsWith("search_chunks_fts_") &&
                    !sqliteTableExists(db, name)
                ) {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.beginTransaction()
            try {
                tableRows.forEach { (table, rows) ->
                    if (table != "room_master_table" && table != "search_chunks_fts" &&
                        !table.startsWith("search_chunks_fts_")) {
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
                if (item.optString("type") != "table" &&
                    item.optString("name") != "room_master_table" &&
                    item.optString("table") != "android_metadata" &&
                    item.optString("table") != "room_master_table") {
                    db.execSQL(item.getString("sql"))
                }
            }
            db.version = schema.optInt("userVersion", 0)
            db.execSQL("PRAGMA foreign_keys=ON")
            // 手工删除父级文件后，指向它的子记录按外键语义一并移除，并写入日志。
            if (onPruned != null) pruneForeignKeyViolations(db, onPruned)
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

    /** 反复删除缺少父记录的行，直到外键检查通过；级联子记录由外键动作处理。 */
    private fun pruneForeignKeyViolations(db: SQLiteDatabase, report: (String) -> Unit) {
        repeat(MAX_PRUNE_ROUNDS) {
            val violations = mutableListOf<Pair<String, Long>>()
            db.rawQuery("PRAGMA foreign_key_check", null).use { cursor ->
                while (cursor.moveToNext()) {
                    if (!cursor.isNull(1)) violations.add(cursor.getString(0) to cursor.getLong(1))
                }
            }
            if (violations.isEmpty()) return
            violations.forEach { (table, rowId) ->
                db.execSQL("DELETE FROM " + quote(table) + " WHERE rowid = ?", arrayOf<Any>(rowId))
            }
            report(
                "Removed " + violations.size + " row(s) whose parent record is missing, in: " +
                    violations.map { it.first }.distinct().joinToString()
            )
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
                    columns.joinToString(",") { quote(it) } + ") VALUES (" +
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
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            return it.moveToFirst()
        }
    }

    private fun cursorValue(cursor: Cursor, index: Int): Any? = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
        Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
        Cursor.FIELD_TYPE_BLOB -> JSONObject().put(BLOB_KEY, Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> JSONObject.NULL
    }

    private fun jsonValue(value: Any?): Any? {
        if (value == null || value === JSONObject.NULL) return null
        if (value is JSONObject && value.has(BLOB_KEY)) return Base64.decode(value.getString(BLOB_KEY), Base64.DEFAULT)
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
