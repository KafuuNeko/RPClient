package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem

/** 世界书列表页状态。 */
sealed class WorldBookListUiState {
    data object None : WorldBookListUiState()

    data class Normal(
        val loadState: WorldBookListLoadState = WorldBookListLoadState.None,
        val lorebooks: List<WorldBookListItem> = emptyList()
    ) : WorldBookListUiState()

    data object Finished : WorldBookListUiState()
}

/** 世界书列表读取或导入期间的加载状态。 */
sealed class WorldBookListLoadState {
    data object None : WorldBookListLoadState()
    data object Loading : WorldBookListLoadState()
}
