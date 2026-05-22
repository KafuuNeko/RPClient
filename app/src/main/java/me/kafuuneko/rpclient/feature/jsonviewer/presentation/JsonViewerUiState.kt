package me.kafuuneko.rpclient.feature.jsonviewer.presentation

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType

sealed class JsonViewerUiState {
    data object None : JsonViewerUiState()

    data class Normal(
        val title: String,
        val path: List<String>,
        val currentType: JsonViewerNodeType,
        val childCount: Int,
        val entries: List<JsonViewerEntry>,
        val canNavigateUp: Boolean
    ) : JsonViewerUiState()

    data class Error(
        val title: String,
        val message: String,
        val rawPreview: String
    ) : JsonViewerUiState()

    data object Finished : JsonViewerUiState()
}

