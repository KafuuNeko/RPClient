package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 单聊页面需要 Activity 执行的剪贴板、文件选择和页面跳转事件。 */
sealed class ChatViewEvent : IViewEvent {
    data object PickImages : ChatViewEvent()

    data object SaveImage : ChatViewEvent()

    data class CopyText(val text: String) : ChatViewEvent()

    data class OpenSession(val sessionId: String) : ChatViewEvent()

    data class OpenChatExporter(val fileName: String) : ChatViewEvent()
}
