package me.kafuuneko.rpclient.feature.requestlog.presentation

import android.net.Uri

/** 请求日志页的用户意图。 */
sealed class RequestLogUiIntent {
    data object Init : RequestLogUiIntent()

    data object Back : RequestLogUiIntent()

    data object LoadMore : RequestLogUiIntent()

    data class ExportRequestJson(val logId: Long) : RequestLogUiIntent()

    data class ExportResponseJson(val logId: Long) : RequestLogUiIntent()

    data class ExportResult(val token: String?, val uri: Uri?) : RequestLogUiIntent()

    data object CancelExport : RequestLogUiIntent()

    data class OpenRequestJson(val logId: Long, val title: String) : RequestLogUiIntent()

    data class OpenResponseJson(val logId: Long, val title: String) : RequestLogUiIntent()

    data object ShowClearConfirmDialog : RequestLogUiIntent()

    data object ConfirmClearLogs : RequestLogUiIntent()

    data object DismissDialog : RequestLogUiIntent()
}
