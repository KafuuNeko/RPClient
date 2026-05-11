package me.kafuuneko.rpclient.feature.requestlog.model

data class RequestLogItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val requestJson: String,
    val responseJson: String
)
