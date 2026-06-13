package me.kafuuneko.rpclient.feature.chat.model

/** 单聊世界书选择器中的条目展示模型及当前会话启用状态。 */
data class ChatLorebookEntryItem(
    val id: Long,
    val lorebookId: Long,
    val lorebookName: String,
    val name: String,
    val keywords: List<String>,
    val secondaryKeywords: List<String>,
    val constant: Boolean,
    val order: Int,
    val depth: Int,
    val content: String,
    val enabled: Boolean
)
