package me.kafuuneko.rpclient.libs.search.storage

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.libs.room.dao.SearchSourceDao
import me.kafuuneko.rpclient.libs.room.entity.SearchSource

private const val CONTENT_PART_POINTS = 16384

/** 分段读取超长源，规避 CursorWindow 单行限制，不截断历史正文。 */
internal suspend fun SearchSourceDao.readSearchText(source: SearchSource): String {
    return buildString {
        var offset = 1
        // SQLite substr 的偏移按码点计数，不能用 Kotlin UTF-16 长度推进。
        while (true) {
            currentCoroutineContext().ensureActive()
            val part = contentPart(
                source.kind,
                source.sourceId,
                source.parentId,
                offset,
                CONTENT_PART_POINTS
            ) ?: break
            append(part)
            val points = part.codePointCount(beginIndex = 0, endIndex = part.length)
            if (points < CONTENT_PART_POINTS) break
            offset += points
        }
    }
}
