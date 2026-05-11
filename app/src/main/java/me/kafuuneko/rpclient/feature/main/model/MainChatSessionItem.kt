package me.kafuuneko.rpclient.feature.main.model

data class MainChatSessionItem(
    val id: String,
    val characterName: String,
    val title: String,
    val preview: String,
    val messageCount: Int,
    val branchCount: Int,
    val updatedAt: String
)

