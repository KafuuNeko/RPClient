package me.kafuuneko.rpclient.feature.character.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class CharacterViewEvent : IViewEvent {
    data object OpenAvatarPicker : CharacterViewEvent()
}
