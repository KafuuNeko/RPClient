package me.kafuuneko.rpclient.libs.search.storage

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.libs.room.dao.ContentSearchDao
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchSql

/** 分段读取超长源，规避 CursorWindow 单行限制，不截断历史正文。 */
internal suspend fun ContentSearchDao.readSearchText(source: SearchSource): String {
    val (table, parent) = SearchSql.sourceTable(source.kind)
    return buildString {
        var offset = 1
        // SQLite substr 的偏移按码点计数，不能用 Kotlin UTF-16 长度推进。
        while (true) {
            currentCoroutineContext().ensureActive()
            val part = text(SimpleSQLiteQuery(
                "SELECT substr(content, ?, 16384) FROM $table WHERE id = ? AND $parent = ?",
                arrayOf<Any?>(offset, source.sourceId, source.parentId)
            )) ?: break
            append(part)
            val points = part.codePointCount(0, part.length)
            if (points < 16384) break
            offset += points
        }
    }
}
