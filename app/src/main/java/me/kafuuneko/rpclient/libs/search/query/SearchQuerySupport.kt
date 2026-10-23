package me.kafuuneko.rpclient.libs.search.query

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
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.search.storage.readSearchText

/** 搜索共有的元数据、片段和导航校验；两种策略保持相同文字与排序语义。 */
internal class SearchQuerySupport(database: AppDatabase) {
    private val mDao = database.getSearchSourceDao()
    private val mOrder = compareByDescending<SearchHit> { it.latestTime }.thenBy { it.kind }.thenByDescending { it.parentId }

    /** 打开前确认目标归属，避免同名对象、删除或重新导入造成错误导航。 */
    suspend fun exists(hit: SearchHit, query: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (mDao.objectsByIds(hit.kind, listOf(hit.parentId)).isEmpty()) return@withContext false
        if (hit.sourceId == 0L) return@withContext true
        val present = mDao.sourceExists(hit.kind, hit.sourceId, hit.parentId)
        if (!present || query.isBlank()) return@withContext present
        // 结果显示后正文可能被编辑；打开前按当前内容复核，防止定位到已经失效的片段。
        val current = if (hit.kind == 3 && hit.snippet.isEmpty()) sourceLabel(hit.kind, hit.sourceId, "")
        else SearchTextCodec.project(
            text = mDao.readSearchText(
                source = SearchSource(
                    kind = hit.kind,
                sourceId = hit.sourceId,
                parentId = hit.parentId,
                ),
            ),
        )
        SearchTextCodec.normalize(current).contains(SearchTextCodec.normalize(query))
    }

    /** 查询历史发言者快照和章节标题，慢速扫描与索引搜索保持相同标注。 */
    suspend fun sourceLabel(kind: Int, sourceId: Long, fallback: String): String =
        if (kind in 1..3) mDao.sourceLabel(kind, sourceId) else fallback

    /** 分批检索标题、章节标题与角色名称，按对象去重后稳定排序。 */
    suspend fun metadata(request: SearchRequest, query: String): List<SearchHit> {
        val hits = LinkedHashMap<String, SearchHit>()
        var kind = 0
        var parent = 0L
        var source = -1L
        // 键集分页避免长章节目录越往后读取越慢；整库正文从不进入这个列表。
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = mDao.metadataBatch(
                filterKind = request.kind.code,
                afterKind = kind,
                afterParentId = parent,
                afterSourceId = source,
                limit = 200,
            )
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val matched = (row.sourceId == 0L && SearchTextCodec.normalize(row.title).contains(query))
                    || row.names.lineSequence().any { SearchTextCodec.normalize(it).contains(query) }
                if (matched) {
                    val hit = row.hit()
                    hits.putIfAbsent(hit.key, hit)
                }
            }
            if (rows.size < 200) break
            rows.last().let { searchObject -> kind = searchObject.kind
            parent = searchObject.parentId
            source = searchObject.sourceId }
        }
        return hits.values.sortedWith(mOrder)
    }

    /** 仅为当前页解码并生成片段，直接扫描路径复用已投影文字，避免重复编码。 */
    fun hydrate(match: SearchChunkMatch, query: String, text: String = SearchTextCodec.decodeTokens(match.tokens)): SearchHit? {
        val at = text.indexOf(query)
        if (at < 0) return null
        // 显示窗口按码点取边界，高亮使用 Compose 所需的 UTF-16 坐标。
        val start = text.offsetByCodePoints(at, -minOf(a = 40, b = text.codePointCount(beginIndex = 0, endIndex = at)))
        val matchEnd = at + query.length
        val end = text.offsetByCodePoints(matchEnd, minOf(a = 80, b = text.codePointCount(matchEnd, text.length)))
        return SearchHit(
            kind = match.kind,
            parentId = match.parentId,
            title = match.title,
            names = match.names,
            latestTime = match.latestTime,
            sourceId = match.sourceId,
            snippet = text.substring(start, end),
            matchStart = at - start,
            matchEnd = matchEnd - start,
            sourceLabel = match.sourceLabel,
        )
    }

    private fun SearchObject.hit(): SearchHit = SearchHit(
        kind = kind,
        parentId = parentId,
        title = title,
        names = names,
        latestTime = latestTime,
        sourceId = sourceId,
    )

    /** 从当前页尾生成阶段锚点，策略与管理会话由统一入口补齐。 */
    fun SearchHit.cursor(revision: Long, phase: Int): SearchCursor =
        SearchCursor(
            revision = revision,
            phase = phase,
            time = latestTime,
            kind = kind,
            id = parentId,
            sourceId = sourceId,
        )

    /** 对象级稳定排序边界；对象内的源 ID 边界由正文策略补充。 */
    fun after(hit: SearchHit, cursor: SearchCursor?): Boolean = cursor == null
        || hit.latestTime < cursor.time || (hit.latestTime == cursor.time && hit.kind > cursor.kind)
        || (hit.latestTime == cursor.time && hit.kind == cursor.kind && hit.parentId < cursor.id)
}
