package me.kafuuneko.rpclient.feature.llmproviderlist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiIntent
import me.kafuuneko.rpclient.feature.llmproviderlist.presentation.LLMProviderListUiState
import me.kafuuneko.rpclient.feature.llmproviderlist.ui.LLMProviderListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 模型供应商列表页面宿主。 */
class LLMProviderListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<LLMProviderListViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is LLMProviderListUiState.Finished) finish()
        }

        LLMProviderListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(LLMProviderListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(LLMProviderListUiIntent.Resume)
    }
}
