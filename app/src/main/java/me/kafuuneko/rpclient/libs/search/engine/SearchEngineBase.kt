package me.kafuuneko.rpclient.libs.search.engine

import me.kafuuneko.rpclient.libs.search.SearchChangedException
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.libs.search.query.SearchQuerySupport

/** 复用元数据阶段，正文扫描与倒排检索分别由具体策略实现。 */
internal abstract class SearchEngineBase(protected val support: SearchQuerySupport) :
    ContentSearchEngine {
    protected data class Snapshot(val revision: Long, val complete: Boolean)

    /** 捕获该策略独立的数据版本与完整性，不要求扫描策略读取任何索引表。 */
    protected abstract suspend fun snapshot(): Snapshot

    /** 使用仓库已校验、规范化的查询；元数据优先，正文阶段排除已返回的对象。 */
    override suspend fun search(
        request: SearchRequest,
        cursor: SearchCursor?,
        onPartial: suspend (List<SearchHit>) -> Unit,
    ): SearchPage {
        val query = request.query
        val initial = snapshot()
        if (cursor != null && cursor.revision != initial.revision) throw SearchChangedException()
        // 公共元数据规则避免两个策略在章节标题、角色名称与阶段边界上漂移。
        val metadata =
            if (request.scope != SearchScope.Body && request.parentId == null) support.metadata(
                request = request,
                query = query,
            ) else emptyList()
        val visible = if (cursor == null || cursor.phase == 0) metadata.filter {
            support.after(
                it,
                cursor
            )
        } else emptyList()
        val page = visible.take(PAGE_SIZE)
        if (page.isNotEmpty()) onPartial(page)
        val result = when {
            visible.size >= PAGE_SIZE && (request.scope != SearchScope.Metadata || visible.size > PAGE_SIZE) ->
                SearchPage(
                    hits = page,
                    next = with(support) {
                        page.last().cursor(
                            revision = initial.revision,
                            phase = 0,
                        )
                    },
                    complete = request.scope == SearchScope.Metadata || initial.complete,
                )

            request.scope == SearchScope.Metadata -> SearchPage(
                hits = page,
                next = null,
                complete = true
            )

            else -> body(
                request = request,
                query = query,
                cursor = cursor,
                initial = initial,
                metadata = page,
                excluded = metadata.mapTo(HashSet()) { it.key },
                onPartial = onPartial,
            )
        }
        // 扫描中途编辑不能发布可继续的旧游标；可保留已得到的部分结果并提示刷新。
        val unchanged = snapshot().revision == initial.revision
        return result.copy(
            next = result.next.takeIf { unchanged },
            complete = result.complete && unchanged
        )
    }

    /** 执行具体正文策略，内存仅保留当前页及一条预读结果。 */
    protected abstract suspend fun body(
        request: SearchRequest,
        query: String,
        cursor: SearchCursor?,
        initial: Snapshot,
        metadata: List<SearchHit>,
        excluded: Set<String>,
        onPartial: suspend (List<SearchHit>) -> Unit,
    ): SearchPage

    protected companion object {
        const val PAGE_SIZE = 20
    }
}
