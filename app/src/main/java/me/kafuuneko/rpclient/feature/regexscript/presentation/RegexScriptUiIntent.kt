package me.kafuuneko.rpclient.feature.regexscript.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.regexscript.model.RegexScriptDraft
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope

sealed class RegexScriptUiIntent {
    data object Init : RegexScriptUiIntent()
    data object Back : RegexScriptUiIntent()
    data class SelectScope(val scope: RegexScriptScope) : RegexScriptUiIntent()
    data class SelectCharacter(val characterId: Long) : RegexScriptUiIntent()
    data class ToggleAuthorization(val authorized: Boolean) : RegexScriptUiIntent()
    data object CreateScript : RegexScriptUiIntent()
    data class EditScript(val scriptId: String) : RegexScriptUiIntent()
    data class CopyScript(val scriptId: String) : RegexScriptUiIntent()
    data class DeleteScriptClick(val scriptId: String) : RegexScriptUiIntent()
    data object ConfirmDeleteScript : RegexScriptUiIntent()
    data class MoveScript(val scriptId: String, val delta: Int) : RegexScriptUiIntent()
    data class UpdateDraft(val draft: RegexScriptDraft) : RegexScriptUiIntent()
    data object SaveDraft : RegexScriptUiIntent()
    data object DismissDialog : RegexScriptUiIntent()
    data class ToggleScriptEnabled(val scriptId: String) : RegexScriptUiIntent()
    data class ChangeTestInput(val value: String) : RegexScriptUiIntent()
    data class SelectTestPlacement(val placement: RegexPlacement) : RegexScriptUiIntent()
    data class SelectTestMode(val mode: RegexExecutionMode) : RegexScriptUiIntent()
    data object RunTest : RegexScriptUiIntent()
    data object ImportClick : RegexScriptUiIntent()
    data class ImportJson(val uri: Uri) : RegexScriptUiIntent()
    data object ExportClick : RegexScriptUiIntent()
    data class ExportJson(val uri: Uri) : RegexScriptUiIntent()
}
