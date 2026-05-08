package me.kafuuneko.rpclient.feature.worldbook.presentation

import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel

sealed class WorldBookUiState {
    data object None : WorldBookUiState()

    data class Normal(
        val selectedLoreBookId: String,
        val loreBooks: List<LoreBookUiModel>,
        val entries: List<LoreEntryUiModel>
    ) : WorldBookUiState()

    data object Finished : WorldBookUiState()
}
