package me.kafuuneko.rpclient.feature.regexscript

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.regexscript.model.RegexScriptDraft
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexCharacterItem
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptDialogState
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiIntent
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiState
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.regex.RegexExecutionContext
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptEngine
import me.kafuuneko.rpclient.libs.regex.RegexScriptRepository
import me.kafuuneko.rpclient.libs.regex.RegexScriptRuntime
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.libs.regex.ScopedRegexScript
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.UUID

class RegexScriptViewModel :
    CoreViewModelWithEvent<RegexScriptUiIntent, RegexScriptUiState>(
        RegexScriptUiState.None
    ), KoinComponent {
    private val mRepository by inject<RegexScriptRepository>()
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mEngine by inject<RegexScriptEngine>()
    private val mContext by inject<Context>()

    @UiIntentObserver(RegexScriptUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<RegexScriptUiState.None>()) return
        val characters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }.map { RegexCharacterItem(it.id, it.name) }
        RegexScriptUiState.Normal(characters = characters)
            .refreshScripts()
            .setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.Back::class)
    private fun onBack() {
        RegexScriptUiState.Finished.setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.SelectScope::class)
    private suspend fun onSelectScope(intent: RegexScriptUiIntent.SelectScope) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(
            scope = intent.scope,
            selectedCharacterId = if (intent.scope == RegexScriptScope.Character) {
                state.selectedCharacterId ?: state.characters.firstOrNull()?.id
            } else {
                state.selectedCharacterId
            },
            dialogState = RegexScriptDialogState.None
        ).refreshScripts().setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.SelectCharacter::class)
    private suspend fun onSelectCharacter(intent: RegexScriptUiIntent.SelectCharacter) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(selectedCharacterId = intent.characterId)
            .refreshScripts()
            .setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.ToggleAuthorization::class)
    private fun onToggleAuthorization(intent: RegexScriptUiIntent.ToggleAuthorization) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        when (state.scope) {
            RegexScriptScope.Global -> Unit
            RegexScriptScope.Preset -> mRepository.setPresetAuthorized(intent.authorized)
            RegexScriptScope.Character -> state.selectedCharacterId?.let {
                mRepository.setCharacterAuthorized(it, intent.authorized)
            }
        }
        state.copy(authorized = intent.authorized).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.CreateScript::class)
    private fun onCreateScript() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        if (state.scope == RegexScriptScope.Character && state.selectedCharacterId == null) return
        val draft = RegexScriptDraft(id = UUID.randomUUID().toString())
        state.copy(
            dialogState = RegexScriptDialogState.Editor(
                draft = draft,
                validationError = validate(draft)
            )
        ).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.EditScript::class)
    private fun onEditScript(intent: RegexScriptUiIntent.EditScript) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val script = state.scripts.firstOrNull { it.id == intent.scriptId } ?: return
        state.copy(
            dialogState = RegexScriptDialogState.Editor(RegexScriptDraft.from(script))
        ).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.CopyScript::class)
    private suspend fun onCopyScript(intent: RegexScriptUiIntent.CopyScript) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val source = state.scripts.firstOrNull { it.id == intent.scriptId } ?: return
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            scriptName = "${source.scriptName} Copy"
        )
        state.saveScripts(state.scripts + copy)
        state.copy(scripts = state.scripts + copy).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.DeleteScriptClick::class)
    private fun onDeleteScriptClick(intent: RegexScriptUiIntent.DeleteScriptClick) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val script = state.scripts.firstOrNull { it.id == intent.scriptId } ?: return
        state.copy(
            dialogState = RegexScriptDialogState.DeleteConfirm(script.id, script.scriptName)
        ).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.ConfirmDeleteScript::class)
    private suspend fun onConfirmDeleteScript() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val dialog = state.dialogState as? RegexScriptDialogState.DeleteConfirm ?: return
        val scripts = state.scripts.filterNot { it.id == dialog.scriptId }
        state.saveScripts(scripts)
        state.copy(scripts = scripts, dialogState = RegexScriptDialogState.None).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.MoveScript::class)
    private suspend fun onMoveScript(intent: RegexScriptUiIntent.MoveScript) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val from = state.scripts.indexOfFirst { it.id == intent.scriptId }
        if (from < 0) return
        val to = (from + intent.delta).coerceIn(0, state.scripts.lastIndex)
        if (from == to) return
        val scripts = state.scripts.toMutableList()
        val moved = scripts.removeAt(from)
        scripts.add(to, moved)
        state.saveScripts(scripts)
        state.copy(scripts = scripts).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.UpdateDraft::class)
    private fun onUpdateDraft(intent: RegexScriptUiIntent.UpdateDraft) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        if (state.dialogState !is RegexScriptDialogState.Editor) return
        state.copy(
            dialogState = RegexScriptDialogState.Editor(
                draft = intent.draft,
                validationError = validate(intent.draft)
            )
        ).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.SaveDraft::class)
    private suspend fun onSaveDraft() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val dialog = state.dialogState as? RegexScriptDialogState.Editor ?: return
        val error = validate(dialog.draft)
        if (error != null) {
            state.copy(dialogState = dialog.copy(validationError = error)).setup()
            return
        }
        val script = dialog.draft.toScript()
        val scripts = state.scripts.toMutableList()
        val index = scripts.indexOfFirst { it.id == script.id }
        if (index < 0) scripts += script else scripts[index] = script
        state.saveScripts(scripts)
        state.copy(scripts = scripts, dialogState = RegexScriptDialogState.None).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(dialogState = RegexScriptDialogState.None).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.ToggleScriptEnabled::class)
    private suspend fun onToggleScriptEnabled(intent: RegexScriptUiIntent.ToggleScriptEnabled) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val scripts = state.scripts.map {
            if (it.id == intent.scriptId) it.copy(disabled = !it.disabled) else it
        }
        state.saveScripts(scripts)
        state.copy(scripts = scripts).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.ChangeTestInput::class)
    private fun onChangeTestInput(intent: RegexScriptUiIntent.ChangeTestInput) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(testInput = intent.value).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.SelectTestPlacement::class)
    private fun onSelectTestPlacement(intent: RegexScriptUiIntent.SelectTestPlacement) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(testPlacement = intent.placement).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.SelectTestMode::class)
    private fun onSelectTestMode(intent: RegexScriptUiIntent.SelectTestMode) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(testMode = intent.mode).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.RunTest::class)
    private fun onRunTest() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        val scripts = state.scripts.mapIndexed { index, script ->
            ScopedRegexScript(script, state.scope, order = index)
        }
        val result = mEngine.execute(
            state.testInput,
            scripts,
            RegexExecutionContext(
                placement = state.testPlacement,
                mode = state.testMode,
                macros = RegexScriptRuntime.macros("User", "Character")
            )
        )
        val errors = result.errors.joinToString("\n") { "${it.scriptName}: ${it.message}" }
        state.copy(
            testOutput = listOf(result.text, errors).filter { it.isNotBlank() }.joinToString("\n\n")
        ).setup()
    }

    @UiIntentObserver(RegexScriptUiIntent.ImportClick::class)
    private fun onImportClick() {
        RegexScriptViewEvent.OpenImporter.tryEmit()
    }

    @UiIntentObserver(RegexScriptUiIntent.ImportJson::class)
    private suspend fun onImportJson(intent: RegexScriptUiIntent.ImportJson) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        if (state.scope == RegexScriptScope.Character && state.selectedCharacterId == null) return
        runCatching {
            val json = withContext(Dispatchers.IO) {
                mContext.contentResolver.openInputStream(intent.uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error(mContext.getString(R.string.regex_import_failed))
            }
            val imported = mRepository.importScripts(json)
            require(imported.isNotEmpty()) { mContext.getString(R.string.regex_import_failed) }
            val existingIds = state.scripts.map { it.id }.toMutableSet()
            val normalized = imported.map { script ->
                if (script.id.isBlank() || !existingIds.add(script.id)) {
                    script.copy(id = UUID.randomUUID().toString())
                } else {
                    script
                }
            }
            val scripts = state.scripts + normalized
            state.saveScripts(scripts)
            state.copy(scripts = scripts).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.regex_import_success).tryEmit()
        }.onFailure {
            AppViewEvent.PopupToastMessage(
                it.message ?: mContext.getString(R.string.regex_import_failed)
            ).tryEmit()
        }
    }

    @UiIntentObserver(RegexScriptUiIntent.ExportClick::class)
    private fun onExportClick() {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        RegexScriptViewEvent.OpenExporter("regex-${state.scope.name.lowercase()}.json").tryEmit()
    }

    @UiIntentObserver(RegexScriptUiIntent.ExportJson::class)
    private suspend fun onExportJson(intent: RegexScriptUiIntent.ExportJson) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        runCatching {
            withContext(Dispatchers.IO) {
                mContext.contentResolver.openOutputStream(intent.uri)?.bufferedWriter()?.use {
                    it.write(mRepository.exportScripts(state.scripts))
                } ?: error(mContext.getString(R.string.regex_export_failed))
            }
            AppViewEvent.PopupToastMessageByResId(R.string.regex_export_success).tryEmit()
        }.onFailure {
            AppViewEvent.PopupToastMessage(
                it.message ?: mContext.getString(R.string.regex_export_failed)
            ).tryEmit()
        }
    }

    private fun validate(draft: RegexScriptDraft): String? {
        if (draft.scriptName.isBlank()) return mContext.getString(R.string.regex_name_required)
        if (draft.placements.isEmpty()) return mContext.getString(R.string.regex_placement_required)
        if (draft.minDepth.isNotBlank() && draft.minDepth.toIntOrNull() == null) {
            return mContext.getString(R.string.regex_depth_invalid)
        }
        if (draft.maxDepth.isNotBlank() && draft.maxDepth.toIntOrNull() == null) {
            return mContext.getString(R.string.regex_depth_invalid)
        }
        val minDepth = draft.minDepth.toIntOrNull()
        val maxDepth = draft.maxDepth.toIntOrNull()
        if (minDepth != null && maxDepth != null && minDepth > maxDepth) {
            return mContext.getString(R.string.regex_depth_invalid)
        }
        return mEngine.validate(draft.toScript())
    }

    private suspend fun RegexScriptUiState.Normal.refreshScripts(): RegexScriptUiState.Normal {
        val characterId = selectedCharacterId ?: characters.firstOrNull()?.id
        val scripts = when (scope) {
            RegexScriptScope.Global -> mRepository.getGlobalScripts()
            RegexScriptScope.Preset -> mRepository.getPresetScripts()
            RegexScriptScope.Character -> characterId?.let { id ->
                withContext(Dispatchers.IO) {
                    mCharacterRepository.getCharacterById(id)
                }?.let(mRepository::getCharacterScripts)
            }.orEmpty()
        }
        val authorized = when (scope) {
            RegexScriptScope.Global -> true
            RegexScriptScope.Preset -> mRepository.isPresetAuthorized()
            RegexScriptScope.Character -> characterId?.let(mRepository::isCharacterAuthorized) ?: false
        }
        return copy(
            selectedCharacterId = characterId,
            scripts = scripts,
            authorized = authorized
        )
    }

    private suspend fun RegexScriptUiState.Normal.saveScripts(scripts: List<RegexScript>) {
        when (scope) {
            RegexScriptScope.Global -> mRepository.saveGlobalScripts(scripts)
            RegexScriptScope.Preset -> mRepository.savePresetScripts(scripts)
            RegexScriptScope.Character -> selectedCharacterId?.let {
                mRepository.saveCharacterScripts(it, scripts)
            }
        }
    }
}
