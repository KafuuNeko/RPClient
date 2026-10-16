package me.kafuuneko.rpclient.feature.appearance.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 仅由 Activity 执行的系统图片选择动作。 */
sealed interface AppearanceViewEvent : IViewEvent {
    data object OpenDialogAppearance : AppearanceViewEvent
    data object PickImage : AppearanceViewEvent
}
