package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class RequestLogViewEvent : IViewEvent {
    data class CopyText(val text: String) : RequestLogViewEvent()
}
