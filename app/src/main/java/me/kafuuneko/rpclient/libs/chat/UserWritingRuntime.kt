package me.kafuuneko.rpclient.libs.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository

/** 输入框代写的累计正文与推理快照，只有正文允许成为待发送草稿。 */
data class UserWritingProgress(val body: String, val reasoning: String)

/**
 * 输入框代写的模型输出适配器。
 * - 统一流式与非流式返回，分离协议推理字段和正文内的兼容标签。
 * - 不创建消息、不执行 Source 正则，也不提交世界书运行时状态。
 */
class UserWritingRuntime(private val mRepository: LLMRepository) {
    /**
     * 生成草稿的累计快照；上层负责保存正文、处理取消与错误。
     * @param provider 本轮固定的模型配置。
     * @param request 已完成预算和协议处理的代写请求。
     * @param routingSessionKey 包含会话类型的模型路由键。
     * @param streaming 是否使用流式请求。
     * @return 独立的正文与推理输出流。
     */
    fun generate(
        provider: LLMProvider,
        request: LLMGenerationRequest,
        routingSessionKey: String,
        streaming: Boolean,
    ): Flow<UserWritingProgress> = flow {
        val parser = UserWritingOutputParser()
        var reasoning = ""
        fun snapshot() = UserWritingProgress(
            body = parser.body,
            reasoning = listOf(reasoning, parser.reasoning).filter { it.isNotBlank() }
                .joinToString("\n").takeLast(UserWritingOutputParser.MAX_REASONING_CHARS),
        )
        // 使用结构化推理通道，兼容正文标签仅交由增量解析器处理。
        if (streaming) {
            mRepository.streamGenerateWithProvider(
                provider = provider,
                request = request,
                routingSessionKey = routingSessionKey,
            ).collect { event ->
                when (event) {
                    is LLMStreamEvent.Delta -> parser.append(event.content)
                    is LLMStreamEvent.ReasoningDelta -> reasoning = (reasoning + event.content)
                        .takeLast(UserWritingOutputParser.MAX_REASONING_CHARS)

                    is LLMStreamEvent.Finished -> parser.finish()
                    LLMStreamEvent.Connected -> Unit
                }
                emit(snapshot())
            }
        } else {
            val response = mRepository.generateWithProvider(
                provider = provider,
                request = request,
                routingSessionKey = routingSessionKey,
                allowReasoningOnly = true,
            )
            parser.append(response.content)
            reasoning =
                response.reasoningContent.takeLast(UserWritingOutputParser.MAX_REASONING_CHARS)
        }
        // 没有终止事件的兼容服务商也统一完成解析。
        parser.finish()
        emit(snapshot())
    }
}
