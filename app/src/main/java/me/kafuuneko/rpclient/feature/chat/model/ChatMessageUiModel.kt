package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.model.MessageContentPart

/** 单聊消息的最终 UI 模型，正文已拆分为普通文本和可折叠推理块。 */
data class ChatMessageUiModel(
    /** 当前记录或列表项的唯一标识。 */
    val id: String,
    /** 当前对象在业务流程中承担的角色。 */
    val role: MessageRole,
    /** 当前消息或生成流程对应的发言角色。 */
    val speaker: String,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 按正文和推理块拆分后的消息展示片段。 */
    val parts: List<MessageContentPart>,
    /** 当前记录对应的时间戳。 */
    val time: String,
    /** 当前文本或 Prompt 项估算得到的 Token 数。 */
    val tokenCount: Int,
    /** 当前消息或请求是否处于流式生成状态。 */
    val isStreaming: Boolean = false
)
