package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/** 模型供应商列表页状态。 */
sealed class LLMProviderListUiState {
    data object None : LLMProviderListUiState()

    data class Normal(
        val providers: List<LLMProvider>,
        val loadState: LLMProviderListLoadState = LLMProviderListLoadState.None
    ) : LLMProviderListUiState()

    data class Finished(val previous: LLMProviderListUiState) : LLMProviderListUiState()

    companion object {
        fun finished(previous: LLMProviderListUiState): LLMProviderListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 供应商配置加载或启停更新状态。 */
sealed class LLMProviderListLoadState {
    data object None : LLMProviderListLoadState()
    data object Loading : LLMProviderListLoadState()
}
