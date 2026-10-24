package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.json.PreparedJsonExport

/** 由 Activity 执行系统保存选择器或 JSON 查看器导航的一次性事件。 */
sealed class RequestLogViewEvent : IViewEvent {
    data class OpenExporter(val export: PreparedJsonExport) : RequestLogViewEvent()

    data class OpenJson(val title: String, val json: String) : RequestLogViewEvent()
}
