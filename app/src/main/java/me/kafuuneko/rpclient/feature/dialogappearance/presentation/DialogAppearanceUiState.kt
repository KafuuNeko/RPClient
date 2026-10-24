package me.kafuuneko.rpclient.feature.dialogappearance.presentation

import me.kafuuneko.rpclient.libs.appearance.DialogAppearance

/** 独立外观草稿与互斥弹窗状态，只有保存动作发布全局参数。 */
sealed interface DialogAppearanceUiState {
    data class Normal(
        val settings: DialogAppearance = DialogAppearance(),
        val dialog: DialogAppearanceDialog = DialogAppearanceDialog.None
    ) : DialogAppearanceUiState
    data class Finished(val previous: Normal) : DialogAppearanceUiState
}

/** 预览与未保存确认互斥，避免窗口遮罩重复叠加。 */
enum class DialogAppearanceDialog { None, Preview, UnsavedChanges }
