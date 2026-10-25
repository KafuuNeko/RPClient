package me.kafuuneko.rpclient.feature.contentsearch.presentation

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchIndexState
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchScope

/** 独立搜索页状态树，输入、范围、准备状态和结果分别对应页面区域。 */
sealed class ContentSearchUiState {
    data object None : ContentSearchUiState()

    data class Normal(
        val inputState: ContentSearchInputState = ContentSearchInputState(),
        val scopeState: ContentSearchScopeState = ContentSearchScopeState(),
        val indexState: SearchIndexState = SearchIndexState.Preparing,
        val resultState: ContentSearchResultState = ContentSearchResultState.Idle
    ) : ContentSearchUiState()

    data class Finished(val previous: ContentSearchUiState) : ContentSearchUiState()
}

data class ContentSearchInputState(val draft: String = "", val submitted: String = "")

data class ContentSearchScopeState(
    val kind: SearchKind = SearchKind.All,
    val scope: SearchScope = SearchScope.All,
    val parentId: Long? = null,
    val parentTitle: String = ""
)

/** 未完成、已停止和失败都不能显示确定的“无结果”。 */
sealed class ContentSearchResultState {
    data object Idle : ContentSearchResultState()

    data class Searching(val items: List<SearchHit> = emptyList()) : ContentSearchResultState()

    data class Partial(
        val items: List<SearchHit>,
        val next: SearchCursor?,
    ) : ContentSearchResultState()

    /** 检索数据已完整覆盖；下一页游标非空时仍可继续加载结果。 */
    data class Complete(
        val items: List<SearchHit>,
        val next: SearchCursor?,
    ) : ContentSearchResultState()

    data class Stopped(val items: List<SearchHit>) : ContentSearchResultState()

    data class Error(val items: List<SearchHit>, @param:StringRes val message: Int) :
        ContentSearchResultState()
}

/** 只提取当前可渲染项，取消和错误保留已得到的真实结果。 */
fun ContentSearchResultState.items(): List<SearchHit> = when (this) {
    ContentSearchResultState.Idle -> emptyList()
    is ContentSearchResultState.Searching -> items
    is ContentSearchResultState.Partial -> items
    is ContentSearchResultState.Complete -> items
    is ContentSearchResultState.Stopped -> items
    is ContentSearchResultState.Error -> items
}

/**
 * 读取可继续分页的游标，检索数据完整不代表已到最后一页。
 *
 * @return 部分或完整结果的下一页游标；不可分页状态返回 null。
 */
fun ContentSearchResultState.nextCursor(): SearchCursor? = when (this) {
    is ContentSearchResultState.Partial -> next
    is ContentSearchResultState.Complete -> next
    ContentSearchResultState.Idle,
    is ContentSearchResultState.Searching,
    is ContentSearchResultState.Stopped,
    is ContentSearchResultState.Error -> null
}
