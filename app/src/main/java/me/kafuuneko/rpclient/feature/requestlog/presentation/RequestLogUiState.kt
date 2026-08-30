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
        /** 当前页面已加载的请求日志列表。 */
        val logs: List<RequestLogItem>,
        /** 当前分页结果是否仍有更多数据可加载。 */
        val canLoadMore: Boolean = false,
        /** 当前列表是否正在加载下一页数据。 */
        val isLoadingMore: Boolean = false,
        /** 当前页面互斥展示的对话框状态。 */
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
