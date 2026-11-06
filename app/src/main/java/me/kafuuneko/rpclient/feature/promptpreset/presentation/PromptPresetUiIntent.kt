package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.libs.prompt.PromptType

/** Prompt 预设页的用户意图。 */
sealed class PromptPresetUiIntent {
    data class Init(val presetId: Long) : PromptPresetUiIntent()

    data object Retry : PromptPresetUiIntent()
    data object CopyTemplate : PromptPresetUiIntent()
    data class ChangeCopyName(val value: String) : PromptPresetUiIntent()
    data object ConfirmCopy : PromptPresetUiIntent()
    data object ConfirmDiscard : PromptPresetUiIntent()

    data object Back : PromptPresetUiIntent()

    data class EditPromptClick(val type: PromptType) : PromptPresetUiIntent()

    data class ChangePromptDraft(val value: String) : PromptPresetUiIntent()

    data object CopyPromptDraft : PromptPresetUiIntent()

    data object SavePrompt : PromptPresetUiIntent()

    data object DismissPromptDialog : PromptPresetUiIntent()
}
