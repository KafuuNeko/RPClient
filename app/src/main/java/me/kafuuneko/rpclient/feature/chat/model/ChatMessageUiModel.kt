package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.ui.message.MessageContentPart

/** 单聊消息的最终 UI 模型，正文已拆分为普通文本和可折叠推理块。 */
data class ChatMessageUiModel(
    val id: String,
    val role: MessageRole,
    val speaker: String,
    val content: String,
    val parts: List<MessageContentPart>,
    val time: String,
    val tokenCount: Int,
    val isStreaming: Boolean = false
)
