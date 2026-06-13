package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 由 Activity 启动系统导入或导出器的一次性事件。 */
sealed class WorldBookListViewEvent : IViewEvent {
    data object OpenWorldBookImporter : WorldBookListViewEvent()

    data class OpenWorldBookExporter(
        val lorebookId: Long,
        val fileName: String
    ) : WorldBookListViewEvent()
}
