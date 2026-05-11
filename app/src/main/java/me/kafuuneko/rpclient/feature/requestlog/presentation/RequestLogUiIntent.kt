package me.kafuuneko.rpclient.feature.requestlog.presentation

sealed class RequestLogUiIntent {
    data object Init : RequestLogUiIntent()

    data object Back : RequestLogUiIntent()

    data class CopyRequestJson(val logId: Long) : RequestLogUiIntent()

    data class CopyResponseJson(val logId: Long) : RequestLogUiIntent()
}
