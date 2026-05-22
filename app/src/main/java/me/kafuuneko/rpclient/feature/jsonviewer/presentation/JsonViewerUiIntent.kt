package me.kafuuneko.rpclient.feature.jsonviewer.presentation

sealed class JsonViewerUiIntent {
    data class Init(val payloadKey: String?) : JsonViewerUiIntent()

    data object Back : JsonViewerUiIntent()

    data class EntrySelected(val entryId: Int) : JsonViewerUiIntent()
}

