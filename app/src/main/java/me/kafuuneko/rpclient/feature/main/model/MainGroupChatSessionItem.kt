package me.kafuuneko.rpclient.feature.main.model

data class MainGroupChatSessionItem(
    val id: String,
    val title: String,
    val memberNames: String,
    val preview: String,
    val messageCount: Int,
    val updatedAt: String
)
