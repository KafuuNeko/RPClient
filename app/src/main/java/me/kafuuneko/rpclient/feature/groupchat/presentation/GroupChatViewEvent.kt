package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class GroupChatViewEvent : IViewEvent {
    data class CopyText(val text: String) : GroupChatViewEvent()
}
