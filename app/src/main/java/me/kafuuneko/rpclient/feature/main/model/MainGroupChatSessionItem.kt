package me.kafuuneko.rpclient.feature.main.model

/** 首页群聊会话卡片所需的扁平展示数据。 */
data class MainGroupChatSessionItem(
    val id: String,
    val title: String,
    val memberNames: String,
    val preview: String,
    val messageCount: Int,
    val updatedAt: String
)
