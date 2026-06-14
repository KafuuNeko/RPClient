package me.kafuuneko.rpclient.feature.chat.model

/** 单聊消息的最终 UI 模型，正文已拆分为普通文本和可折叠推理块。 */
data class ChatMessageUiModel(
    val id: String,
    val role: MessageRole,
    val speaker: String,
    val content: String,
    val parts: List<ChatMessageContentPart>,
    val time: String,
    val tokenCount: Int,
    val isStreaming: Boolean = false
)

/** 一条消息中可独立渲染的内容分片。 */
sealed class ChatMessageContentPart {
    /** 普通 Markdown 文本。 */
    data class Text(val content: String) : ChatMessageContentPart()
    /** 从 `<think>` 标签解析出的可折叠推理文本。 */
    data class Think(
        val id: String,
        val content: String,
        val isComplete: Boolean = true
    ) : ChatMessageContentPart()
}
