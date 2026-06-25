package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType

/** Prompt 预设页状态，编辑文本通过对话框子状态承载。 */
sealed class PromptPresetUiState {
    data object None : PromptPresetUiState()

    data class Normal(
        val promptValues: Map<PromptType, String>,
        val dialogState: PromptPresetDialogState = PromptPresetDialogState.None
    ) : PromptPresetUiState()

    data class Finished(val previous: PromptPresetUiState) : PromptPresetUiState()

    companion object {
        fun finished(previous: PromptPresetUiState): PromptPresetUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** Prompt 预设页当前显示的对话框。 */
sealed class PromptPresetDialogState {
    data object None : PromptPresetDialogState()

    data class EditPrompt(
        val type: PromptType,
        val draftText: String
    ) : PromptPresetDialogState()
}
