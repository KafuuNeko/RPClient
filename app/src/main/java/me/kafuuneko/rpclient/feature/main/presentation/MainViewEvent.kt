package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 主页面需要 Activity 执行的系统文件选择、图片选择及外链跳转事件。 */
sealed class MainViewEvent : IViewEvent {
    data class OpenUserAvatarPicker(val personaId: Long) : MainViewEvent()

    data object OpenChatImporter : MainViewEvent()

    data object OpenChatImageDirectory : MainViewEvent()

    data class OpenExternalUrl(val url: String) : MainViewEvent()
}
