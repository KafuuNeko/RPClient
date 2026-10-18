package me.kafuuneko.rpclient.libs.room.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.SearchSource
import me.kafuuneko.rpclient.libs.search.SearchChangedException
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchObject
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.libs.search.SearchSql
import me.kafuuneko.rpclient.libs.search.SearchTextCodec

/**
 * 独立内容搜索的数据入口。
 * - 元数据分批投影；全文通过 FTS 先按对象聚合，再做稳定分页。
 * - 只读取当前页命中片段，源代次不一致时拒绝旧结果。
 * - 取消由 Room 的挂起查询传递到底层，CPU 循环主动检查取消。
 */
class ContentSearchQueryRepository(database: AppDatabase) {
    private val mDao = database.getContentSearchDao()
    private val mOrder = compareByDescending<SearchHit> { it.latestTime }.thenBy { it.kind }.thenByDescending { it.parentId }

    /** 提交一页查询，元数据命中可提前回传；所有回调均位于后台线程。 */
    suspend fun search(
        request: SearchRequest,
        cursor: SearchCursor? = null,
        onMetadata: suspend (List<SearchHit>) -> Unit = {}
    ): SearchPage = withContext(Dispatchers.IO) {
        val query = SearchTextCodec.normalize(request.query)
        require(query.isNotBlank() && query.codePointCount(0, query.length) <= SearchTextCodec.MAX_QUERY_POINTS) { "Invalid search query" }
        val progress = mDao.progress()
        val revision = progress.first { it.kind == 0 }.cursor
        if (cursor != null && cursor.revision != revision) throw SearchChangedException()
        val formatReady = progress.any { it.kind == -1 && it.cursor == SearchTextCodec.FORMAT_VERSION }
        val complete = formatReady && progress.filter { it.kind > 0 }.all { it.complete } && !mDao.hasPending()
        // 元数据与正文分阶段分页，正文阶段排除已经由元数据返回的对象。
        val metadata = if (request.scope != SearchScope.Body && request.parentId == null) metadata(request, query) else emptyList()
        val visibleMetadata = if (cursor == null || cursor.phase == 0) metadata.filter { after(it, cursor) } else emptyList()
        val page = visibleMetadata.take(PAGE_SIZE).toMutableList()
        if (page.isNotEmpty()) onMetadata(page.toList())
        if (visibleMetadata.size >= PAGE_SIZE && (request.scope != SearchScope.Metadata || visibleMetadata.size > PAGE_SIZE)) {
            return@withContext SearchPage(page, page.last().cursor(revision, 0), request.scope == SearchScope.Metadata || complete)
        }
        if (request.scope == SearchScope.Metadata) return@withContext SearchPage(page, null, true)
        val excluded = metadata.mapTo(HashSet()) { it.key }
        if (request.directScan) return@withContext directPage(request, query, cursor, revision, page, excluded, onMetadata)
        if (!formatReady) return@withContext SearchPage(page, null, false)
        var boundary = cursor?.takeIf { it.phase == 1 }
        var more = false
        // 不能先截取固定数量的消息再去重；SQL 已聚合对象，跳过元数据命中后继续取页。
        while (page.size <= PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val matches = bodyMatches(request, query, boundary)
            if (matches.isEmpty()) break
            for (match in matches) {
                boundary = SearchCursor(revision, 1, match.latestTime, match.kind, match.parentId, match.sourceId)
                if ("${match.kind}:${match.parentId}" in excluded) continue
                val hit = hydrate(match, query) ?: continue
                if (page.size == PAGE_SIZE) { more = true; break }
                page += hit
            }
            if (more || matches.size < PAGE_SIZE + 1) break
        }
        // 索引正在补建时允许展示这次真实取得的片段，下一页会检查版本并要求刷新。
        val next = if (more) page.last().cursor(revision, 1) else null
        SearchPage(page, next, complete && revision == mDao.progress().first { it.kind == 0 }.cursor)
    }

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
    private suspend fun sourceLabel(kind: Int, sourceId: Long, fallback: String): String = when (kind) {
        1 -> mDao.text(SimpleSQLiteQuery("""SELECT CASE WHEN m.source = 'User' THEN s.userName ELSE c.name END
            FROM chat_messages m JOIN chat_sessions s ON s.id = m.sessionId JOIN character c ON c.id = s.characterId
            WHERE m.id = ?""".trimIndent(), arrayOf(sourceId))).orEmpty()
        2 -> mDao.text(SimpleSQLiteQuery("SELECT speakerNameSnapshot FROM group_chat_messages WHERE id = ?", arrayOf(sourceId))).orEmpty()
        3 -> mDao.text(SimpleSQLiteQuery("SELECT title FROM story_chapters WHERE id = ?", arrayOf(sourceId))).orEmpty()
        else -> fallback
    }

    /** 用户明确选择的慢速路径，遍历原始正文，不依赖全文索引是否可用。 */
    private suspend fun directPage(
        request: SearchRequest, query: String, cursor: SearchCursor?, revision: Long,
        metadata: List<SearchHit>, excluded: Set<String>, onPartial: suspend (List<SearchHit>) -> Unit
    ): SearchPage {
        val limit = PAGE_SIZE - metadata.size
        val candidates = LinkedHashMap<String, SearchHit>()
        val order = mOrder.thenByDescending { it.sourceId }
        // 仅保存本页及一条预读候选；全库扫描可取消，内存不随命中条数增加。
        for (kind in 1..3) {
            if (request.kind.code != 0 && request.kind.code != kind) continue
            val (table, parentColumn) = SearchSql.sourceTable(kind)
            val visible = if (kind == 1) "AND source != 'Summary'" else ""
            var sourceId = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val rows = mDao.sources(SimpleSQLiteQuery("""
                    SELECT $kind kind, id sourceId, $parentColumn parentId, 1 revision, 0 indexedRevision, 0 deleted, 1 dirty, 0 changedTime
                    FROM $table WHERE id > ? $visible AND (? IS NULL OR $parentColumn = ?) ORDER BY id LIMIT 32
                """.trimIndent(), arrayOf<Any?>(sourceId, request.parentId, request.parentId)))
                val parents = rows.map { it.parentId }.distinct()
                if (parents.isEmpty()) break
                val objects = mDao.objects(SimpleSQLiteQuery(
                    "SELECT * FROM (${SearchSql.objects}) WHERE kind = ? AND parentId IN (${parents.joinToString { "?" }})",
                    (listOf<Any>(kind) + parents).toTypedArray()
                )).associateBy { it.parentId }
                for (source in rows) {
                    currentCoroutineContext().ensureActive()
                    if ("$kind:${source.parentId}" in excluded) continue
                    val owner = objects[source.parentId] ?: continue
                    val text = mDao.readSearchText(source)
                    val normalized = withContext(Dispatchers.Default) { SearchTextCodec.normalize(SearchTextCodec.project(text)) }
                    val chunk = SearchTextCodec.chunks(normalized).firstOrNull { it.second.contains(query) } ?: continue
                    val match = SearchChunkMatch(kind, source.parentId, source.sourceId, 0, owner.latestTime,
                        owner.title, owner.names, "", sourceLabel(kind, source.sourceId, owner.names))
                    val hit = hydrate(match, query, chunk.second) ?: continue
                    val boundary = cursor?.takeIf { it.phase == 1 }
                    if (!after(hit, boundary) && !(request.parentId != null && boundary != null &&
                            hit.latestTime == boundary.time && hit.parentId == boundary.id && hit.sourceId < boundary.sourceId)) continue
                    val key = if (request.parentId == null) hit.key else "${hit.key}:${hit.sourceId}"
                    candidates.putIfAbsent(key, hit)
                    if (candidates.size > limit + 1) {
                        val last = candidates.values.maxWithOrNull(order)!!
                        candidates.remove(if (request.parentId == null) last.key else "${last.key}:${last.sourceId}")
                    }
                }
                onPartial(metadata + candidates.values.sortedWith(order).take(limit))
                sourceId = rows.last().sourceId
                if (rows.size < 32) break
            }
        }
        val hits = metadata + candidates.values.sortedWith(order).take(limit)
        val unchanged = revision == mDao.progress().first { it.kind == 0 }.cursor
        return SearchPage(hits, if (unchanged && candidates.size > limit) hits.last().cursor(revision, 1) else null, unchanged)
    }

    private suspend fun metadata(request: SearchRequest, query: String): List<SearchHit> {
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

    private suspend fun bodyMatches(request: SearchRequest, query: String, cursor: SearchCursor?): List<SearchChunkMatch> {
        val scoped = request.parentId != null
        val group = if (scoped) "c.kind, c.parentId, c.sourceId" else "c.kind, c.parentId"
        val time = cursor?.time ?: Long.MAX_VALUE
        val kind = cursor?.kind ?: 0
        val id = cursor?.id ?: Long.MAX_VALUE
        val source = cursor?.sourceId ?: Long.MAX_VALUE
        // 先选定本页行号，再读 token 与发言者，避免为所有候选加载大字段后排序。
        // MATCH 参数是应用编码的短语；范围、排序边界和用户内容都使用参数绑定。
        return mDao.matches(SimpleSQLiteQuery("""
            WITH matched AS (
                SELECT MIN(c.rowid) rowid FROM search_chunks_fts
                JOIN search_chunks c ON c.rowid = search_chunks_fts.rowid
                JOIN search_sources s ON s.kind = c.kind AND s.sourceId = c.sourceId
                WHERE search_chunks_fts MATCH ? AND s.dirty = 0 AND s.deleted = 0 AND s.revision = c.revision
                    AND (? = 0 OR c.kind = ?) AND (? IS NULL OR c.parentId = ?)
                GROUP BY $group
            )
            , selected AS (
                SELECT c.kind, c.parentId, c.sourceId, c.rowid, o.latestTime, o.title, o.names
                FROM matched m JOIN search_chunks c ON c.rowid = m.rowid
                JOIN (${SearchSql.objects}) o ON o.kind = c.kind AND o.parentId = c.parentId
                WHERE o.latestTime < ? OR (o.latestTime = ? AND c.kind > ?)
                    OR (o.latestTime = ? AND c.kind = ? AND c.parentId < ?)
                    OR (? = 1 AND o.latestTime = ? AND c.kind = ? AND c.parentId = ? AND c.sourceId < ?)
                ORDER BY o.latestTime DESC, c.kind, c.parentId DESC, c.sourceId DESC LIMIT ${PAGE_SIZE + 1}
            )
            SELECT p.*, c.tokens,
                CASE p.kind WHEN 1 THEN IFNULL((SELECT CASE WHEN m.source = 'User' THEN s.userName ELSE p.names END
                        FROM chat_messages m JOIN chat_sessions s ON s.id = m.sessionId WHERE m.id = p.sourceId), '')
                    WHEN 2 THEN IFNULL((SELECT speakerNameSnapshot FROM group_chat_messages WHERE id = p.sourceId), '')
                    WHEN 3 THEN IFNULL((SELECT title FROM story_chapters WHERE id = p.sourceId), '')
                    ELSE p.names END sourceLabel
            FROM selected p JOIN search_chunks c ON c.rowid = p.rowid
            ORDER BY p.latestTime DESC, p.kind, p.parentId DESC, p.sourceId DESC
        """.trimIndent(), arrayOf<Any?>(SearchTextCodec.phrase(query), request.kind.code, request.kind.code,
            request.parentId, request.parentId, time, time, kind, time, kind, id,
            if (scoped) 1 else 0, time, kind, id, source)))
    }

    /** 仅为当前页解码并生成片段，直接扫描路径复用已投影文字，避免重复编码。 */
    private fun hydrate(match: SearchChunkMatch, query: String, text: String = SearchTextCodec.decodeTokens(match.tokens)): SearchHit? {
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

    private fun SearchHit.cursor(revision: Long, phase: Int): SearchCursor =
        SearchCursor(revision, phase, latestTime, kind, parentId, sourceId)

    private fun after(hit: SearchHit, cursor: SearchCursor?): Boolean = cursor == null ||
        hit.latestTime < cursor.time || (hit.latestTime == cursor.time && hit.kind > cursor.kind) ||
        (hit.latestTime == cursor.time && hit.kind == cursor.kind && hit.parentId < cursor.id)

    companion object { private const val PAGE_SIZE = 20 }
}
