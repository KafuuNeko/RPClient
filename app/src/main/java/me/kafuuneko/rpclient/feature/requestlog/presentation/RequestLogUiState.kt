package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem

sealed class RequestLogUiState {
    data object None : RequestLogUiState()

    data class Normal(
        val logs: List<RequestLogItem>
    ) : RequestLogUiState()

    data object Finished : RequestLogUiState()
}
