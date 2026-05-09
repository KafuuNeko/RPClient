package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

sealed class LLMProviderListUiState {
    data object None : LLMProviderListUiState()

    data class Normal(
        val providers: List<LLMProvider>,
        val loadState: LLMProviderListLoadState = LLMProviderListLoadState.None
    ) : LLMProviderListUiState()

    data object Finished : LLMProviderListUiState()
}

sealed class LLMProviderListLoadState {
    data object None : LLMProviderListLoadState()
    data object Loading : LLMProviderListLoadState()
}
