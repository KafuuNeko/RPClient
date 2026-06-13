package me.kafuuneko.rpclient.feature.chat.model

/** 单聊模型生成状态，与普通页面加载状态分离。 */
sealed class ChatGenerationState {
    /** 当前没有生成任务。 */
    data object Idle : ChatGenerationState()
    /** 请求已发出但尚未收到流式增量。 */
    data object Requesting : ChatGenerationState()
    /** 正在生成；[content] 为尚未完成持久化的原始累计文本。 */
    data class Streaming(
        val messageId: Long?,
        val content: String
    ) : ChatGenerationState()
    /** 本轮生成失败，消息用于页面提示。 */
    data class Failed(val message: String) : ChatGenerationState()
}
