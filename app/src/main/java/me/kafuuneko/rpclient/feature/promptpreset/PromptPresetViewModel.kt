package me.kafuuneko.rpclient.feature.promptpreset

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

class PromptPresetViewModel : CoreViewModelWithEvent<PromptPresetUiIntent, PromptPresetUiState>(
    PromptPresetUiState.None
) {

    @UiIntentObserver(PromptPresetUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptPresetUiState.None>()) return
        PromptPresetUiState.Normal(
            mainPrompt = AppModel.mainPrompt,
            summarizePrompt = AppModel.summarizePrompt
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.Back::class)
    private fun onBack() {
        PromptPresetUiState.Finished.setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.EditPromptClick::class)
    private fun onEditPromptClick(intent: PromptPresetUiIntent.EditPromptClick) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val currentText = when (intent.type) {
            PromptType.Main -> uiState.mainPrompt
            PromptType.Summarize -> uiState.summarizePrompt
        }
        uiState.copy(
            dialogState = PromptPresetDialogState.EditPrompt(
                type = intent.type,
                currentText = currentText
            )
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.SavePrompt::class)
    private fun onSavePrompt(intent: PromptPresetUiIntent.SavePrompt) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        when (dialog.type) {
            PromptType.Main -> AppModel.mainPrompt = intent.text
            PromptType.Summarize -> AppModel.summarizePrompt = intent.text
        }
        uiState.copy(
            mainPrompt = AppModel.mainPrompt,
            summarizePrompt = AppModel.summarizePrompt,
            dialogState = PromptPresetDialogState.None
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.DismissPromptDialog::class)
    private fun onDismissPromptDialog() {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        uiState.copy(dialogState = PromptPresetDialogState.None).setup()
    }
}
