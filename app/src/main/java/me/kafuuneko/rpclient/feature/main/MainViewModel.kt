package me.kafuuneko.rpclient.feature.main

import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

class MainViewModel : CoreViewModelWithEvent<MainUiIntent, MainUiState>(
    MainUiState.None
) {
    /**
     * 页面初始化
     */
    @UiIntentObserver(MainUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<MainUiState.None>()) return
        MainUiState.Normal("Init").setup()
    }

    @UiIntentObserver(MainUiIntent.Resume::class)
    private suspend fun onResume() {

    }

    /**
     * 页面返回逻辑
     */
    @UiIntentObserver(MainUiIntent.Back::class)
    private suspend fun onBack() {
        MainUiState.Finished.setup()
    }

}