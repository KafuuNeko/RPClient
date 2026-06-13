package me.kafuuneko.rpclient.feature.jsonviewer.presentation

/** JSON 查看器的初始化、返回和节点下钻意图。 */
sealed class JsonViewerUiIntent {
    data class Init(val payloadKey: String?) : JsonViewerUiIntent()

    data object Back : JsonViewerUiIntent()

    data class EntrySelected(val entryId: Int) : JsonViewerUiIntent()
}
