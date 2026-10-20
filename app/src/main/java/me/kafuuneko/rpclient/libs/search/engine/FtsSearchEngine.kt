package me.kafuuneko.rpclient.libs.search.engine

import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.search.SearchChunkMatch
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchSql
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

}
