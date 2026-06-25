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

/**
 * Regex 管理页 ViewModel。
 *
 * 负责作用域切换、角色授权、脚本增删改排序、即时校验、测试以及 JSON 文件读写；
 * Compose 只渲染状态并发送用户意图。
 */
class RegexScriptViewModel :
    CoreViewModelWithEvent<RegexScriptUiIntent, RegexScriptUiState>(
        RegexScriptUiState.None
    ), KoinComponent {
    /** Regex 持久化、角色扩展字段及授权状态的统一入口。 */
    private val mRepository by inject<RegexScriptRepository>()
    /** 提供角色选择列表以及角色卡最新数据。 */
    private val mCharacterRepository by inject<CharacterRepository>()
    /** 编辑校验和测试模式共用的纯执行引擎。 */
    private val mEngine by inject<RegexScriptEngine>()
    /** 仅用于 ContentResolver 和本地化错误文案。 */
    private val mContext by inject<Context>()

    /** 首次加载角色列表，并刷新默认全局作用域。 */
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
        RegexScriptUiState.finished(uiStateFlow.value).setup()
    }

    /** 切换作用域，并在进入角色作用域时自动选择首个角色。 */
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

    /** 切换当前角色，并读取该角色的内嵌脚本及授权状态。 */
    @UiIntentObserver(RegexScriptUiIntent.SelectCharacter::class)
    private suspend fun onSelectCharacter(intent: RegexScriptUiIntent.SelectCharacter) {
        val state = getOrNull<RegexScriptUiState.Normal>() ?: return
        state.copy(selectedCharacterId = intent.characterId)
            .refreshScripts()
            .setup()
    }

    /** 更新预设或角色作用域授权；全局脚本始终允许执行。 */
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

    /** 创建带即时校验结果的空白脚本草稿。 */
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

    /** 复制脚本并生成新 ID，同时保留未知 JSON 扩展字段。 */
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

    /** 按拖动方向移动一个位置并立即持久化新顺序。 */
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

    /** 每次表单变化都重新编译 Find Regex，向 UI 返回即时错误。 */
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

    /** 校验并保存草稿；已有 ID 原位覆盖，新 ID 追加到列表末尾。 */
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

    /** 在当前作用域脚本上运行纯测试，不改变持久化数据。 */
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

    /** 读取外部 JSON，修复空 ID 或冲突 ID 后追加到当前作用域。 */
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

    /** 将当前作用域脚本写入用户选择的 JSON 文档。 */
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

    /** 校验必填字段、深度区间以及 Regex 编译结果。 */
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

    /** 根据当前作用域重读脚本与授权状态，避免页面持有跨作用域旧数据。 */
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

    /** 将列表保存到当前全局、预设或角色卡作用域。 */
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
