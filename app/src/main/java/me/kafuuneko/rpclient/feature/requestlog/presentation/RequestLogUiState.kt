package me.kafuuneko.rpclient.feature.requestlog.presentation

import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem

/** 请求日志页当前显示的确认对话框。 */
sealed class RequestLogDialogState {
    data object None : RequestLogDialogState()
    data object ClearConfirm : RequestLogDialogState()
}

/** 请求日志页状态。 */
sealed class RequestLogUiState {
    data object None : RequestLogUiState()

    data class Normal(
        val logs: List<RequestLogItem>,
        val dialogState: RequestLogDialogState = RequestLogDialogState.None
    ) : RequestLogUiState()

    data class Finished(val previous: RequestLogUiState) : RequestLogUiState()

    companion object {
        fun finished(previous: RequestLogUiState): RequestLogUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}
