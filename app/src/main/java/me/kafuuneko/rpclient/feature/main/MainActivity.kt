package me.kafuuneko.rpclient.feature.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.feature.main.ui.MainLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 应用主页面宿主，承载首页与全局设置。 */
class MainActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<MainViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is MainUiState.Finished) finish()
        }

        MainLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(MainUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(MainUiIntent.Resume)
    }
}
