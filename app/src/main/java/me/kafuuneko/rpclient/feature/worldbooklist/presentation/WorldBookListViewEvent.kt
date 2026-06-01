package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import me.kafuuneko.rpclient.libs.core.IViewEvent

sealed class WorldBookListViewEvent : IViewEvent {
    data object OpenWorldBookImporter : WorldBookListViewEvent()

    data class OpenWorldBookExporter(
        val lorebookId: Long,
        val fileName: String
    ) : WorldBookListViewEvent()
}
