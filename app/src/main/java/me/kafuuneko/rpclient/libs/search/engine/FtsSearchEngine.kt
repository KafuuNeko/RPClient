package me.kafuuneko.rpclient.libs.search.engine

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchTextCodec
import me.kafuuneko.rpclient.libs.search.query.SearchQuerySupport

/** 用户成功启用后的倒排检索策略，脏源与未完成代次不会返回旧正文。 */
internal class FtsSearchEngine(database: AppDatabase, support: SearchQuerySupport) : SearchEngineBase(support) {
    private val mDao = database.getContentSearchDao()

    /** 当前格式与全部播种检查点是发布完整结果的前置条件。 */
    override suspend fun snapshot(): Snapshot {
        val progress = mDao.progress()
        check(progress.any { it.kind == -1 && it.cursor == SearchTextCodec.FORMAT_VERSION }) { "Search format requires rebuild" }
        return Snapshot(progress.first { it.kind == 0 }.cursor,
            progress.count { it.kind > 0 && it.complete } == 3 && !mDao.hasPending())
    }

    /** 对象聚合在 SQL 内完成，不先截取固定数量消息再去重。 */
    override suspend fun body(
        request: SearchRequest, query: String, cursor: SearchCursor?, initial: Snapshot,
        metadata: List<SearchHit>, excluded: Set<String>, onPartial: suspend (List<SearchHit>) -> Unit
    ): SearchPage {
        val page = metadata.toMutableList()
        var boundary = cursor?.takeIf { it.phase == 1 }
        var more = false
        // 不能先截取固定数量的消息再去重；SQL 已聚合对象，跳过元数据命中后继续取页。
        while (page.size <= PAGE_SIZE) {
            currentCoroutineContext().ensureActive()
            val matches = bodyMatches(request, query, boundary)
            if (matches.isEmpty()) break
            for (match in matches) {
                boundary = SearchCursor(initial.revision, 1, match.latestTime, match.kind, match.parentId, match.sourceId)
                if ("${match.kind}:${match.parentId}" in excluded) continue
                val hit = support.hydrate(match, query) ?: continue
                if (page.size == PAGE_SIZE) { more = true; break }
                page += hit
            }
            if (more || matches.size < PAGE_SIZE + 1) break
        }
        // 索引正在补建时允许展示这次真实取得的片段，下一页会检查版本并要求刷新。
        val next = if (more) with(support) { page.last().cursor(initial.revision, 1) } else null
        return SearchPage(page, next, initial.complete)
    }

    /** 只为当前页读取 token，源修订与归属始终在 SQL 中复核。 */
    private suspend fun bodyMatches(request: SearchRequest, query: String, cursor: SearchCursor?): List<SearchChunkMatch> =
        mDao.matchPage(
            phrase = SearchTextCodec.phrase(query),
            filterKind = request.kind.code,
            parentId = request.parentId,
            afterTime = cursor?.time ?: Long.MAX_VALUE,
            afterKind = cursor?.kind ?: 0,
            afterParentId = cursor?.id ?: Long.MAX_VALUE,
            afterSourceId = cursor?.sourceId ?: Long.MAX_VALUE,
            limit = PAGE_SIZE + 1
        )
}
