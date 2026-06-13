package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 角色编辑页需要 Activity 调用系统组件处理的一次性事件。 */
sealed class CharacterEditViewEvent : IViewEvent {
    data object OpenAvatarPicker : CharacterEditViewEvent()
}
