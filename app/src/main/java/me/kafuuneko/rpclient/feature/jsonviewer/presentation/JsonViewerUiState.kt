package me.kafuuneko.rpclient.feature.jsonviewer.presentation

import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerEntry
import me.kafuuneko.rpclient.feature.jsonviewer.model.JsonViewerNodeType

/**
 * JSON 查看器页面状态。
 *
 * Normal 仅保存当前层级的扁平条目，完整 JSON 树由 ViewModel 持有，避免状态对象重复复制。
 */
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

    data class Finished(val previous: JsonViewerUiState) : JsonViewerUiState()

    companion object {
        fun finished(previous: JsonViewerUiState): JsonViewerUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}
