package me.kafuuneko.rpclient.feature.requestlog.model

/** 请求日志列表展示模型，保留完整 JSON 供复制和详情查看。 */
data class RequestLogItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val requestJson: String,
    val responseJson: String
)
