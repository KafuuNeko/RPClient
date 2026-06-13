package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 模型供应商列表页状态。 */
sealed class LLMProviderListUiState {
    data object None : LLMProviderListUiState()

    data class Normal(
        val providers: List<LLMProvider>,
        val loadState: LLMProviderListLoadState = LLMProviderListLoadState.None
    ) : LLMProviderListUiState()

    data object Finished : LLMProviderListUiState()
}

/** 供应商配置加载或启停更新状态。 */
sealed class LLMProviderListLoadState {
    data object None : LLMProviderListLoadState()
    data object Loading : LLMProviderListLoadState()
}
