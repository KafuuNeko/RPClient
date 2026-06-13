package me.kafuuneko.rpclient.feature.regexscript.presentation

import me.kafuuneko.rpclient.feature.regexscript.model.RegexScriptDraft
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope

sealed class RegexScriptUiState {
    data object None : RegexScriptUiState()

    data class Normal(
        val scope: RegexScriptScope = RegexScriptScope.Global,
        val characters: List<RegexCharacterItem> = emptyList(),
        val selectedCharacterId: Long? = null,
        val scripts: List<RegexScript> = emptyList(),
        val authorized: Boolean = true,
        val dialogState: RegexScriptDialogState = RegexScriptDialogState.None,
        val testInput: String = "",
        val testOutput: String = "",
        val testPlacement: RegexPlacement = RegexPlacement.UserInput,
        val testMode: RegexExecutionMode = RegexExecutionMode.Source
    ) : RegexScriptUiState()

    data object Finished : RegexScriptUiState()
}
data class RegexCharacterItem(
    val id: Long,
    val name: String
)

sealed class RegexScriptDialogState {
    data object None : RegexScriptDialogState()

    data class Editor(
        val draft: RegexScriptDraft,
        val validationError: String? = null
    ) : RegexScriptDialogState()

    data class DeleteConfirm(
        val scriptId: String,
        val scriptName: String
    ) : RegexScriptDialogState()
}
