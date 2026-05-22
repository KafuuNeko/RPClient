package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class RequestLogViewEvent : IViewEvent {
    data class CopyText(val text: String) : RequestLogViewEvent()

    data class OpenJson(val title: String, val json: String) : RequestLogViewEvent()
}
