package me.kafuuneko.rpclient.feature.contentsearch

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiIntent
import me.kafuuneko.rpclient.feature.contentsearch.presentation.ContentSearchUiState
import me.kafuuneko.rpclient.feature.contentsearch.ui.ContentSearchLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.search.SearchKind

/** 独立搜索页宿主，只传递初始分类和页面生命周期。 */
class ContentSearchActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<ContentSearchViewModel>()

    /** 导航及提示使用通用一次性事件。 */
    override fun getViewEventFlow() = mViewModel.viewEventFlow

    /** 页面恢复只交给 ViewModel 决定刷新，不由宿主查询数据。 */
    override fun onResume() {
        super.onResume()
        mViewModel.emit(ContentSearchUiIntent.Resume)
    }

    /** 读取稳定类型参数；旧或非法值退回全部。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = SearchKind.entries.firstOrNull { it.code == intent.getIntExtra(EXTRA_KIND, 0) } ?: SearchKind.All
        mViewModel.emit(ContentSearchUiIntent.Init(kind))
    }

    /** Finished 继续渲染最后一帧，由 Activity 完成退出。 */
    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) { if (state is ContentSearchUiState.Finished) finish() }
        ContentSearchLayout(state) { mViewModel.emit(this) }
    }

    companion object { const val EXTRA_KIND = "search_kind" }
}
