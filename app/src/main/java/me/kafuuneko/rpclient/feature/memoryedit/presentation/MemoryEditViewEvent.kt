package me.kafuuneko.rpclient.feature.memoryedit.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 系统选择器由 Activity 执行，结果包括取消都返回 ViewModel。 */
sealed class MemoryEditViewEvent : IViewEvent {
    data object OpenImporter : MemoryEditViewEvent()
    data object OpenExporter : MemoryEditViewEvent()
}
