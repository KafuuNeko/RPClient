package me.kafuuneko.rpclient.model

/** 单聊与群聊共用的消息历史加载状态，同一窗口每次只加载一个方向。 */
sealed class MessageHistoryLoadState {
    data object None : MessageHistoryLoadState()

    data object LoadingOlder : MessageHistoryLoadState()

    data object LoadingNewer : MessageHistoryLoadState()

    /** 是否已有分页任务在执行，用于阻止两个方向交错修改历史窗口。 */
    val isBusy: Boolean get() = this !is None

    /** 列表头部是否应展示加载反馈。 */
    val isLoadingOlder: Boolean get() = this is LoadingOlder

    /** 列表尾部是否应展示加载反馈。 */
    val isLoadingNewer: Boolean get() = this is LoadingNewer
}
