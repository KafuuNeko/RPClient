package me.kafuuneko.rpclient.feature.requestlog.presentation

sealed class RequestLogUiIntent {
    data object Init : RequestLogUiIntent()

    data object Back : RequestLogUiIntent()

    data class CopyRequestJson(val logId: Long) : RequestLogUiIntent()

    data class CopyResponseJson(val logId: Long) : RequestLogUiIntent()

    data class OpenRequestJson(val logId: Long, val title: String) : RequestLogUiIntent()

    data class OpenResponseJson(val logId: Long, val title: String) : RequestLogUiIntent()

    data object ShowClearConfirmDialog : RequestLogUiIntent()

    data object ConfirmClearLogs : RequestLogUiIntent()

    data object DismissDialog : RequestLogUiIntent()
}
