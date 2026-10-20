package me.kafuuneko.rpclient.libs.search.query

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchObject
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchSql
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.search.storage.readSearchText

/** 搜索共有的元数据、片段和导航校验；两种策略保持相同文字与排序语义。 */
internal class SearchQuerySupport(database: AppDatabase) {
    private val mDao = database.getContentSearchDao()
    private val mOrder = compareByDescending<SearchHit> { it.latestTime }.thenBy { it.kind }.thenByDescending { it.parentId }

    /** 打开前确认目标归属，避免同名对象、删除或重新导入造成错误导航。 */
    suspend fun exists(hit: SearchHit, query: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (objects(hit.kind, hit.parentId).isEmpty()) return@withContext false
        if (hit.sourceId == 0L) return@withContext true
        val (table, parent) = SearchSql.sourceTable(hit.kind)
        val visible = if (hit.kind == 1) "AND source != 'Summary'" else ""
        val present = mDao.text(SimpleSQLiteQuery(
            "SELECT 'exists' FROM $table WHERE id = ? AND $parent = ? $visible", arrayOf<Any?>(hit.sourceId, hit.parentId)
        )) != null
        if (!present || query.isBlank()) return@withContext present
        // 结果显示后正文可能被编辑；打开前按当前内容复核，防止定位到已经失效的片段。
        val current = if (hit.kind == 3 && hit.snippet.isEmpty()) sourceLabel(hit.kind, hit.sourceId, "")
        else SearchTextCodec.project(mDao.readSearchText(SearchSource(kind = hit.kind, sourceId = hit.sourceId, parentId = hit.parentId)))
        SearchTextCodec.normalize(current).contains(SearchTextCodec.normalize(query))
    }

    /** 查询历史发言者快照和章节标题，慢速扫描与索引搜索保持相同标注。 */
    suspend fun sourceLabel(kind: Int, sourceId: Long, fallback: String): String = when (kind) {
        1 -> mDao.text(SimpleSQLiteQuery("""SELECT CASE WHEN m.source = 'User' THEN s.userName ELSE c.name END
            FROM chat_messages m JOIN chat_sessions s ON s.id = m.sessionId JOIN character c ON c.id = s.characterId
            WHERE m.id = ?""".trimIndent(), arrayOf(sourceId))).orEmpty()
        2 -> mDao.text(SimpleSQLiteQuery("SELECT speakerNameSnapshot FROM group_chat_messages WHERE id = ?", arrayOf(sourceId))).orEmpty()
        3 -> mDao.text(SimpleSQLiteQuery("SELECT title FROM story_chapters WHERE id = ?", arrayOf(sourceId))).orEmpty()
        else -> fallback
    }

    /** 分批检索标题、章节标题与角色名称，按对象去重后稳定排序。 */
    suspend fun metadata(request: SearchRequest, query: String): List<SearchHit> {
        val hits = LinkedHashMap<String, SearchHit>()
        var kind = 0
        var parent = 0L
        var source = -1L
        // 键集分页避免长章节目录越往后读取越慢；整库正文从不进入这个列表。
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = mDao.objects(SimpleSQLiteQuery("""
                SELECT * FROM (${SearchSql.metadata}) WHERE (? = 0 OR kind = ?)
                AND (kind > ? OR (kind = ? AND parentId > ?) OR (kind = ? AND parentId = ? AND sourceId > ?))
                ORDER BY kind, parentId, sourceId LIMIT 200
            """.trimIndent(), arrayOf<Any?>(request.kind.code, request.kind.code, kind, kind, parent, kind, parent, source)))
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val matched = (row.sourceId == 0L && SearchTextCodec.normalize(row.title).contains(query)) ||
                    row.names.lineSequence().any { SearchTextCodec.normalize(it).contains(query) }
                if (matched) {
                    val hit = row.hit()
                    hits.putIfAbsent(hit.key, hit)
                }
            }
            if (rows.size < 200) break
            rows.last().let { kind = it.kind; parent = it.parentId; source = it.sourceId }
        }
        return hits.values.sortedWith(mOrder)
    }

    /** 仅为当前页解码并生成片段，直接扫描路径复用已投影文字，避免重复编码。 */
    fun hydrate(match: SearchChunkMatch, query: String, text: String = SearchTextCodec.decodeTokens(match.tokens)): SearchHit? {
        val at = text.indexOf(query)
        if (at < 0) return null
        // 显示窗口按码点取边界，高亮使用 Compose 所需的 UTF-16 坐标。
        val start = text.offsetByCodePoints(at, -minOf(40, text.codePointCount(0, at)))
        val matchEnd = at + query.length
        val end = text.offsetByCodePoints(matchEnd, minOf(80, text.codePointCount(matchEnd, text.length)))
        return SearchHit(match.kind, match.parentId, match.title, match.names, match.latestTime,
            match.sourceId, text.substring(start, end), at - start, matchEnd - start, match.sourceLabel)
    }

    private suspend fun objects(kind: Int, id: Long): List<SearchObject> = mDao.objects(SimpleSQLiteQuery(
        "SELECT * FROM (${SearchSql.objects}) WHERE kind = ? AND parentId = ?", arrayOf<Any?>(kind, id)
    ))

    private fun SearchObject.hit(): SearchHit = SearchHit(kind, parentId, title, names, latestTime, sourceId)

    /** 从当前页尾生成阶段锚点，策略与管理会话由统一入口补齐。 */
    fun SearchHit.cursor(revision: Long, phase: Int): SearchCursor =
        SearchCursor(revision, phase, latestTime, kind, parentId, sourceId)

    /** 对象级稳定排序边界；对象内的源 ID 边界由正文策略补充。 */
    fun after(hit: SearchHit, cursor: SearchCursor?): Boolean = cursor == null ||
        hit.latestTime < cursor.time || (hit.latestTime == cursor.time && hit.kind > cursor.kind) ||
        (hit.latestTime == cursor.time && hit.kind == cursor.kind && hit.parentId < cursor.id)
}
