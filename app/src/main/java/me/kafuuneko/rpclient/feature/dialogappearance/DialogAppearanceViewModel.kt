package me.kafuuneko.rpclient.feature.dialogappearance

import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAdjustment
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceDialog
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceUiIntent
import me.kafuuneko.rpclient.feature.dialogappearance.presentation.DialogAppearanceUiState
import me.kafuuneko.rpclient.libs.appearance.DialogAppearance
import me.kafuuneko.rpclient.libs.appearance.DialogAppearanceManager
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 对话框外观草稿持有者，负责参数校验、显式保存和退出确认。 */
class DialogAppearanceViewModel : CoreViewModelWithEvent<DialogAppearanceUiIntent, DialogAppearanceUiState>(
    DialogAppearanceUiState.Normal()
), KoinComponent {
    private val mAppearance by inject<DialogAppearanceManager>()
    private val mOriginal = mAppearance.settings.value

    init { DialogAppearanceUiState.Normal(mOriginal).setup() }

    /** 滑动期间仅调整草稿，页面以同一参数即时渲染示例。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Adjust::class)
    private fun onAdjust(intent: DialogAppearanceUiIntent.Adjust) {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        if (state.dialog != DialogAppearanceDialog.None) return
        val settings = state.settings
        val next = when (intent.field) {
            DialogAdjustment.Transparency -> settings.copy(transparency = intent.value)
            DialogAdjustment.Blur -> settings.copy(blur = intent.value)
            DialogAdjustment.ControlTransparency -> settings.copy(controlTransparency = intent.value)
            DialogAdjustment.Dim -> settings.copy(dim = intent.value)
            DialogAdjustment.BehindBlur -> settings.copy(behindBlur = intent.value)
        }
        state.copy(settings = next.normalized()).setup()
    }

    /** 使用真实 Dialog 窗口验证当前草稿，不保存到全局。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Preview::class)
    private fun onPreview() {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        if (state.dialog == DialogAppearanceDialog.None) state.copy(dialog = DialogAppearanceDialog.Preview).setup()
    }

    /** 关闭预览或取消返回，继续保留编辑中的草稿。 */
    @UiIntentObserver(DialogAppearanceUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        getOrNull<DialogAppearanceUiState.Normal>()?.copy(dialog = DialogAppearanceDialog.None)?.setup()
    }

    /** 恢复默认只改变草稿，返回时仍遵循未保存确认。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Reset::class)
    private fun onReset() {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        if (state.dialog == DialogAppearanceDialog.None) state.copy(settings = DialogAppearance()).setup()
    }

    /** 提交完整外观并关闭编辑器，其他 Activity 的弹窗同步更新。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Save::class)
    private fun onSave() {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        mAppearance.save(state.settings)
        DialogAppearanceUiState.Finished(state.copy(dialog = DialogAppearanceDialog.None)).setup()
    }

    /** 返回先关闭当前示例；有修改时询问保存，无修改直接离开。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Back::class)
    private fun onBack() {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        when {
            state.dialog != DialogAppearanceDialog.None -> onDismissDialog()
            state.settings != mOriginal -> state.copy(dialog = DialogAppearanceDialog.UnsavedChanges).setup()
            else -> DialogAppearanceUiState.Finished(state).setup()
        }
    }

    /** 用户明确放弃后丢弃草稿，已提交的全局外观保持不变。 */
    @UiIntentObserver(DialogAppearanceUiIntent.Discard::class)
    private fun onDiscard() {
        val state = getOrNull<DialogAppearanceUiState.Normal>() ?: return
        if (state.dialog == DialogAppearanceDialog.UnsavedChanges) {
            DialogAppearanceUiState.Finished(state.copy(dialog = DialogAppearanceDialog.None)).setup()
        }
    }
}
