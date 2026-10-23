package me.kafuuneko.rpclient.libs.search.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.search.query.SearchQuerySupport
import me.kafuuneko.rpclient.libs.search.storage.SearchDataRevision
import me.kafuuneko.rpclient.libs.search.storage.readSearchText

/** 普通数据库扫描；完全不读取或生成 FTS 进度、修订与待办。 */
internal class DatabaseScanSearchEngine(
    database: AppDatabase, support: SearchQuerySupport, private val mRevision: SearchDataRevision
) : SearchEngineBase(support) {
    private val mDao = database.getSearchSourceDao()
    private val mOrder = compareByDescending<SearchHit> { it.latestTime }.thenBy { it.kind }
        .thenByDescending { it.parentId }

    /** 只观察业务表写入，关闭索引后仍可正确拒绝过期分页。 */
    override suspend fun snapshot(): Snapshot =
        Snapshot(revision = mRevision.current(), complete = true)

    /** 用户明确选择的慢速路径，遍历原始正文，不依赖全文索引是否可用。 */
    override suspend fun body(
        request: SearchRequest,
        query: String,
        cursor: SearchCursor?,
        initial: Snapshot,
        metadata: List<SearchHit>,
        excluded: Set<String>,
        onPartial: suspend (List<SearchHit>) -> Unit,
    ): SearchPage {
        val limit = PAGE_SIZE - metadata.size
        val candidates = LinkedHashMap<String, SearchHit>()
        val order = mOrder.thenByDescending { it.sourceId }
        // 仅保存本页及一条预读候选；全库扫描可取消，内存不随命中条数增加。
        for (kind in 1..3) {
            if (request.kind.code != 0 && request.kind.code != kind) continue
            var sourceId = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val rows = mDao.sourceBatch(
                    kind = kind,
                    afterId = sourceId,
                    parentId = request.parentId,
                    limit = 32
                )
                val parents = rows.map { it.parentId }.distinct()
                if (parents.isEmpty()) break
                val objects = mDao.objectsByIds(kind, parents).associateBy { it.parentId }
                for (source in rows) {
                    currentCoroutineContext().ensureActive()
                    if ("$kind:${source.parentId}" in excluded) continue
                    val owner = objects[source.parentId] ?: continue
                    val text = mDao.readSearchText(source)
                    val normalized = withContext(Dispatchers.Default) {
                        SearchTextCodec.normalize(
                            SearchTextCodec.project(text)
                        )
                    }
                    val chunk =
                        SearchTextCodec.chunks(normalized).firstOrNull { it.second.contains(query) }
                            ?: continue
                    val match = SearchChunkMatch(
                        kind = kind,
                        parentId = source.parentId,
                        sourceId = source.sourceId,
                        rowid = 0,
                        latestTime = owner.latestTime,
                        title = owner.title,
                        names = owner.names,
                        tokens = "",
                        sourceLabel = support.sourceLabel(
                            kind = kind,
                            sourceId = source.sourceId,
                            fallback = owner.names,
                        ),
                    )
                    val hit = support.hydrate(match, query, chunk.second) ?: continue
                    val boundary = cursor?.takeIf { it.phase == 1 }
                    val isOutsideCursorBoundary = !support.after(hit, boundary)
                        && !(request.parentId != null
                        && boundary != null
                        && hit.latestTime == boundary.time
                        && hit.parentId == boundary.id
                        && hit.sourceId < boundary.sourceId)
                    if (isOutsideCursorBoundary) continue
                    val key =
                        if (request.parentId == null) hit.key else "${hit.key}:${hit.sourceId}"
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
        return SearchPage(
            hits = hits,
            next = if (candidates.size > limit) with(support) {
                hits.last().cursor(
                    revision = initial.revision,
                    phase = 1,
                )
            } else null,
            complete = true,
        )
    }
}
