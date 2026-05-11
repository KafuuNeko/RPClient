package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem

sealed class WorldBookListUiState {
    data object None : WorldBookListUiState()

    data class Normal(
        val loadState: WorldBookListLoadState = WorldBookListLoadState.None,
        val lorebooks: List<WorldBookListItem> = emptyList()
    ) : WorldBookListUiState()

    data object Finished : WorldBookListUiState()
}

sealed class WorldBookListLoadState {
    data object None : WorldBookListLoadState()
    data object Loading : WorldBookListLoadState()
}

