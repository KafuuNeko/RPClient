package me.kafuuneko.rpclient.feature.promptpreset

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetDialogState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetLoadState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.prompt.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.PromptType
import me.kafuuneko.rpclient.libs.prompt.availableMacros
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetConflictException
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 单套提示词模板的详情与编辑状态持有者。
 * - 默认预设只读，自定义正文逐项保存。
 * - revision 冲突和保存失败保留草稿，未保存退出需明确放弃。
 */
class PromptPresetViewModel :
    CoreViewModelWithEvent<PromptPresetUiIntent, PromptPresetUiState>(PromptPresetUiState.None),
    KoinComponent {
    private val mRepository by inject<PromptPresetRepository>()
    private val mContext by inject<Context>()
    private var mPresetId = PromptTemplateSnapshot.BUILTIN_ID

    /** 页面参数确定编辑目标，不把查看模板视为启用。 */
    @UiIntentObserver(PromptPresetUiIntent.Init::class)
    private fun onInit(intent: PromptPresetUiIntent.Init) {
        if (!isStateOf<PromptPresetUiState.None>()) return
        mPresetId = intent.presetId
        PromptPresetUiState.Normal(
            promptValues = emptyMap(),
            loadState = PromptPresetLoadState.Loading
        ).setup()
        perform { refresh() }
    }

    /** 读取失败后重试同一模板，不自动退回另一套配置。 */
    @UiIntentObserver(PromptPresetUiIntent.Retry::class)
    private fun onRetry() = perform { refresh() }

    /** 未保存编辑先询问放弃，普通返回保留最后一帧。 */
    @UiIntentObserver(PromptPresetUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        if (state.dialogState != PromptPresetDialogState.None) {
            onDismissPromptDialog()
            return
        }
        PromptPresetUiState.finished(state).setup()
    }

    /** 读取已保存文本作为草稿；点击默认预设仅提示无法编辑。 */
    @UiIntentObserver(PromptPresetUiIntent.EditPromptClick::class)
    private fun onEditPromptClick(intent: PromptPresetUiIntent.EditPromptClick) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy || state.loadState != PromptPresetLoadState.Ready) return
        if (state.isBuiltin) {
            AppViewEvent.PopupToastMessageByResId(R.string.prompt_template_read_only).tryEmit()
        } else {
            state.copy(dialogState = editor(state, intent.type)).setup()
        }
    }

    /** 复制系统模板或当前自定义模板，保存名称前不落库。 */
    @UiIntentObserver(PromptPresetUiIntent.CopyTemplate::class)
    private fun onCopyTemplate() = showCopyDialog()

    /** 更新副本名称草稿。 */
    @UiIntentObserver(PromptPresetUiIntent.ChangeCopyName::class)
    private fun onChangeCopyName(intent: PromptPresetUiIntent.ChangeCopyName) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.CopyTemplate ?: return
        if (!state.busy) state.copy(dialogState = dialog.copy(name = intent.value)).setup()
    }

    /** 创建独立副本并在当前页面编辑，保持全局选择不变。 */
    @UiIntentObserver(PromptPresetUiIntent.ConfirmCopy::class)
    private fun onConfirmCopy() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.CopyTemplate ?: return
        if (dialog.name.isBlank()) return
        perform {
            mPresetId = mRepository.create(mPresetId, dialog.name, state.description)
            refresh()
        }
    }

    /** 剪贴板操作交给宿主，复制内容来自未保存草稿。 */
    @UiIntentObserver(PromptPresetUiIntent.CopyPromptDraft::class)
    private fun onCopyPromptDraft() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        PromptPresetViewEvent.CopyText(dialog.draftText).tryEmit()
    }

    /** 更新草稿原文，不裁剪空白或替换宏。 */
    @UiIntentObserver(PromptPresetUiIntent.ChangePromptDraft::class)
    private fun onChangePromptDraft(intent: PromptPresetUiIntent.ChangePromptDraft) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        if (!state.busy) state.copy(dialogState = dialog.copy(draftText = intent.value)).setup()
    }

    /** 正文和版本在同一事务保存，成功后才关闭编辑器。 */
    @UiIntentObserver(PromptPresetUiIntent.SavePrompt::class)
    private fun onSavePrompt() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val dialog = state.dialogState as? PromptPresetDialogState.EditPrompt ?: return
        perform {
            mRepository.savePrompt(mPresetId, state.revision, dialog.type, dialog.draftText)
            refresh()
        }
    }

    /** 关闭前比较原文；失败草稿不会因返回而静默丢失。 */
    @UiIntentObserver(PromptPresetUiIntent.DismissPromptDialog::class)
    private fun onDismissPromptDialog() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        val dialog = state.dialogState
        if (dialog is PromptPresetDialogState.EditPrompt && dialog.draftText != dialog.originalText) {
            state.copy(dialogState = PromptPresetDialogState.Discard(dialog)).setup()
        } else if (dialog is PromptPresetDialogState.Discard) {
            state.copy(dialogState = dialog.editor).setup()
        } else perform { refresh() }
    }

    /** 用户明确放弃草稿后重新加载，解决其他页面编辑导致的版本冲突。 */
    @UiIntentObserver(PromptPresetUiIntent.ConfirmDiscard::class)
    private fun onConfirmDiscard() = perform { refresh() }

    /** 读取实体只用于转换，不让页面访问 DAO 或实体。 */
    private suspend fun refresh() {
        // 同时刷新正文与 revision，关闭冲突草稿后才能以新版本继续编辑。
        val detail = mRepository.getDetail(mPresetId)
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        val builtin = detail.preset.id == PromptTemplateSnapshot.BUILTIN_ID
        state.copy(
            promptValues = detail.snapshot.values,
            name = if (builtin) mContext.getString(R.string.prompt_template_default) else detail.preset.name,
            description = detail.preset.description,
            revision = detail.preset.revision,
            isBuiltin = builtin,
            loadState = PromptPresetLoadState.Ready,
            dialogState = PromptPresetDialogState.None,
        ).setup()
    }

    /** 用已保存原文建立草稿，空串保持为空，默认文本只供显式重置。 */
    private fun editor(state: PromptPresetUiState.Normal, type: PromptType) =
        PromptPresetDialogState.EditPrompt(
            type = type,
            draftText = state.promptValues[type].orEmpty(),
            defaultText = type.defaultValue,
            availableMacros = availableMacros(type),
            originalText = state.promptValues[type].orEmpty(),
        )

    /** 工具栏复制时先确认副本名称。 */
    private fun showCopyDialog() {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy || state.loadState != PromptPresetLoadState.Ready) return
        state.copy(
            dialogState = PromptPresetDialogState.CopyTemplate(
                mContext.getString(
                    R.string.prompt_template_copy_name,
                    state.name
                )
            )
        ).setup()
    }

    /** 异步操作统一处理取消和失败，错误不包含用户提示词。 */
    private fun perform(block: suspend () -> Unit) {
        val state = getOrNull<PromptPresetUiState.Normal>() ?: return
        if (state.busy) return
        state.copy(busy = true).setup()
        // 操作异常只转为本地化提示，原文草稿与选择留在当前状态。
        viewModelScope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val current = getOrNull<PromptPresetUiState.Normal>() ?: return@launch
                if (current.loadState != PromptPresetLoadState.Ready) current.copy(loadState = PromptPresetLoadState.Failed)
                    .setup()
                AppViewEvent.PopupToastMessageByResId(if (error is PromptPresetConflictException) R.string.prompt_template_conflict else R.string.prompt_template_failed)
                    .tryEmit()
            } finally {
                getOrNull<PromptPresetUiState.Normal>()?.copy(busy = false)?.setup()
            }
        }
    }
}
