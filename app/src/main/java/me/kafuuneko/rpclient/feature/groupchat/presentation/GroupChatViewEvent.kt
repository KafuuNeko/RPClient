package me.kafuuneko.rpclient.feature.groupchat.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 群聊页交由 Activity 执行的一次性系统动作，避免把消费状态保存在 UiState 中。 */
sealed class GroupChatViewEvent : IViewEvent {
    data object PickImages : GroupChatViewEvent()
    data object SaveImage : GroupChatViewEvent()
    data class CopyText(val text: String) : GroupChatViewEvent()
}
