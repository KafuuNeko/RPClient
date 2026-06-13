package me.kafuuneko.rpclient.feature.main.model

/** 首页最近单聊会话卡片所需的扁平展示数据。 */
data class MainChatSessionItem(
    val id: String,
    val characterId: String,
    val characterName: String,
    val title: String,
    val preview: String,
    val messageCount: Int,
    val updatedAt: String
)
