package me.kafuuneko.rpclient.feature.promptpreset

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.libs.AppModel
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver

/** Prompt 预设页状态持有者，集中读写 AppModel 中的模板覆盖值。 */
class PromptPresetViewModel : CoreViewModelWithEvent<PromptPresetUiIntent, PromptPresetUiState>(
    PromptPresetUiState.None
) {

    @UiIntentObserver(PromptPresetUiIntent.Init::class)
    private fun onInit() {
        if (!isStateOf<PromptPresetUiState.None>()) return
        PromptPresetUiState.Normal(
            promptValues = readPromptValues()
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.Back::class)
    private fun onBack() {
        PromptPresetUiState.Finished.setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.EditPromptClick::class)
    private fun onEditPromptClick(intent: PromptPresetUiIntent.EditPromptClick) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        uiState.copy(
            dialogState = PromptPresetDialogState.EditPrompt(
                type = intent.type,
                currentText = uiState.promptValues[intent.type].orEmpty()
            )
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.SavePrompt::class)
    private fun onSavePrompt(intent: PromptPresetUiIntent.SavePrompt) {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = uiState.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        writePrompt(dialog.type, intent.text)
        uiState.copy(
            promptValues = readPromptValues(),
            dialogState = PromptPresetDialogState.None
        ).setup()
    }

    @UiIntentObserver(PromptPresetUiIntent.DismissPromptDialog::class)
    private fun onDismissPromptDialog() {
        val uiState = getOrNull<PromptPresetUiState.Normal>() ?: return
        uiState.copy(dialogState = PromptPresetDialogState.None).setup()
    }

    private fun readPromptValues(): Map<PromptType, String> {
        return PromptType.entries.associateWith { readPrompt(it) }
    }

    private fun readPrompt(type: PromptType): String {
        return when (type) {
            PromptType.Main -> AppModel.mainPrompt
            PromptType.Auxiliary -> AppModel.auxiliaryPrompt
            PromptType.PostHistory -> AppModel.postHistoryInstructions
            PromptType.Summarize -> AppModel.summarizePrompt
            PromptType.Impersonation -> AppModel.impersonationPrompt
            PromptType.NewChat -> AppModel.newChatPrompt
            PromptType.NewExampleChat -> AppModel.newExampleChatPrompt
            PromptType.ContinueNudge -> AppModel.continueNudgePrompt
            PromptType.ReplaceEmptyMessage -> AppModel.replaceEmptyMessagePrompt
            PromptType.WorldInfoFormat -> AppModel.worldInfoFormat
            PromptType.ScenarioFormat -> AppModel.scenarioFormat
            PromptType.PersonalityFormat -> AppModel.personalityFormat
            PromptType.GroupNudge -> AppModel.groupNudgePrompt
            PromptType.NewGroupChat -> AppModel.newGroupChatPrompt
            PromptType.GroupSummarize -> AppModel.groupSummarizePrompt
        }
    }

    private fun writePrompt(type: PromptType, text: String) {
        when (type) {
            PromptType.Main -> AppModel.mainPrompt = text
            PromptType.Auxiliary -> AppModel.auxiliaryPrompt = text
            PromptType.PostHistory -> AppModel.postHistoryInstructions = text
            PromptType.Summarize -> AppModel.summarizePrompt = text
            PromptType.Impersonation -> AppModel.impersonationPrompt = text
            PromptType.NewChat -> AppModel.newChatPrompt = text
            PromptType.NewExampleChat -> AppModel.newExampleChatPrompt = text
            PromptType.ContinueNudge -> AppModel.continueNudgePrompt = text
            PromptType.ReplaceEmptyMessage -> AppModel.replaceEmptyMessagePrompt = text
            PromptType.WorldInfoFormat -> AppModel.worldInfoFormat = text
            PromptType.ScenarioFormat -> AppModel.scenarioFormat = text
            PromptType.PersonalityFormat -> AppModel.personalityFormat = text
            PromptType.GroupNudge -> AppModel.groupNudgePrompt = text
            PromptType.NewGroupChat -> AppModel.newGroupChatPrompt = text
            PromptType.GroupSummarize -> AppModel.groupSummarizePrompt = text
        }
    }
}
