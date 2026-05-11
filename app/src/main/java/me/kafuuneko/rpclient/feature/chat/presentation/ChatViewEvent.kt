package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class ChatViewEvent : IViewEvent {
    data class CopyText(val text: String) : ChatViewEvent()
}
