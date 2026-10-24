package me.kafuuneko.rpclient.feature.requestlog.model

/** 日志列表一次分页读取的展示项及后续页标记。 */
internal data class RequestLogPage(
    val items: List<RequestLogItem>,
    val canLoadMore: Boolean
)
