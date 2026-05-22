package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType

sealed class PromptPresetUiState {
    data object None : PromptPresetUiState()

    data class Normal(
        val promptValues: Map<PromptType, String>,
        val dialogState: PromptPresetDialogState = PromptPresetDialogState.None
    ) : PromptPresetUiState()

    data object Finished : PromptPresetUiState()
}

sealed class PromptPresetDialogState {
    data object None : PromptPresetDialogState()

    data class EditPrompt(
        val type: PromptType,
        val currentText: String
    ) : PromptPresetDialogState()
}
