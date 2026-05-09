package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType

sealed class PromptPresetUiIntent {
    data object Init : PromptPresetUiIntent()

    data object Back : PromptPresetUiIntent()

    data class EditPromptClick(val type: PromptType) : PromptPresetUiIntent()

    data class SavePrompt(val text: String) : PromptPresetUiIntent()

    data object DismissPromptDialog : PromptPresetUiIntent()
}
