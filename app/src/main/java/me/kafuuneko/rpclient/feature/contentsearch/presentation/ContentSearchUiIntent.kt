package me.kafuuneko.rpclient.feature.contentsearch.presentation

import me.kafuuneko.rpclient.libs.search.SearchHit
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchScope

/** 搜索页只在提交、切换已提交范围或主动翻页时执行查询。 */
sealed class ContentSearchUiIntent {
    data class Init(val kind: SearchKind) : ContentSearchUiIntent()

    data object Resume : ContentSearchUiIntent()

    data object Back : ContentSearchUiIntent()

    data class ChangeQuery(val value: String) : ContentSearchUiIntent()

    data class SelectKind(val kind: SearchKind) : ContentSearchUiIntent()

    data class SelectScope(val scope: SearchScope) : ContentSearchUiIntent()

    data object SearchDirectly : ContentSearchUiIntent()

    data object Submit : ContentSearchUiIntent()

    data object Stop : ContentSearchUiIntent()

    data object More : ContentSearchUiIntent()

    data object OpenSearchSettings : ContentSearchUiIntent()

    data class Open(val hit: SearchHit, val locate: Boolean) : ContentSearchUiIntent()

    data class SearchWithin(val hit: SearchHit) : ContentSearchUiIntent()

    data object ClearObjectScope : ContentSearchUiIntent()
}
