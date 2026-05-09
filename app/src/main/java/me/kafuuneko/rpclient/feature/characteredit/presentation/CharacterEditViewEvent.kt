package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class CharacterEditViewEvent : IViewEvent {
    data object OpenAvatarPicker : CharacterEditViewEvent()
}
