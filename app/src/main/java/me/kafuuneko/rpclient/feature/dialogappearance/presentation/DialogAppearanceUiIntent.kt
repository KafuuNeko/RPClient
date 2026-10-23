package me.kafuuneko.rpclient.feature.dialogappearance.presentation

/** 对话框外观编辑动作，滑块只修改本页草稿。 */
sealed interface DialogAppearanceUiIntent {
    data class Adjust(val field: DialogAdjustment, val value: Int) : DialogAppearanceUiIntent

    data object Preview : DialogAppearanceUiIntent

    data object DismissDialog : DialogAppearanceUiIntent

    data object Reset : DialogAppearanceUiIntent

    data object Save : DialogAppearanceUiIntent

    data object Back : DialogAppearanceUiIntent

    data object Discard : DialogAppearanceUiIntent
}

/** 两种模糊均为无单位强度，百分比参数和模糊参数不会混用。 */
enum class DialogAdjustment { Transparency,

Blur,

ControlTransparency,

Dim,

BehindBlur }
