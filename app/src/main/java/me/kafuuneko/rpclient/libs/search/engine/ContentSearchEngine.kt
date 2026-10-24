package me.kafuuneko.rpclient.libs.search.engine

import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchPage
import me.kafuuneko.rpclient.libs.search.SearchRequest

/** 统一的搜索策略，回调与结果均使用同一分页、取消和完整性契约。 */
internal interface ContentSearchEngine {
    /** 查询一页，回调只发布本次查询的真实部分结果。 */
    suspend fun search(request: SearchRequest, cursor: SearchCursor?, onPartial: suspend (List<SearchHit>) -> Unit): SearchPage
}
