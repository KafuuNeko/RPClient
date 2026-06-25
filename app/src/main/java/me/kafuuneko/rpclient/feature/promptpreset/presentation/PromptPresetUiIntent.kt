package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType

/** Prompt 预设页的用户意图。 */
sealed class PromptPresetUiIntent {
    data object Init : PromptPresetUiIntent()

    data object Back : PromptPresetUiIntent()

    data class EditPromptClick(val type: PromptType) : PromptPresetUiIntent()

    data class ChangePromptDraft(val value: String) : PromptPresetUiIntent()

    data object SavePrompt : PromptPresetUiIntent()

    data object DismissPromptDialog : PromptPresetUiIntent()
}
