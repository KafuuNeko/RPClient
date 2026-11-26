package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn

/**
 * 在所有协议共用的发送入口强制前置模型级系统指令。
 *
 * - 独立于上下文构建与预算裁剪，已最终化请求也必须注入，额度由用户自行预留。
 * - 在 Prompt 后处理之后注入，避免单条用户消息等模式改写指令角色或顺序。
 * - 位于用量统计器外层，使流式与非流式统计都包含实际发送的系统指令。
 */
internal class LLMSystemPromptClient(
    private val mDelegate: LLMClient,
    private val mSystemPrompt: String
) : LLMClient {
    /** Agent 在预算之前已经注入时不重复追加系统指令。 */
    override suspend fun generateToolTurn(request: LLMToolGenerationRequest): LLMToolTurn =
        mDelegate.generateToolTurn(request.withToolSystemPrompt())

    /** 流式工具请求使用相同的系统指令标记。 */
    override fun streamToolTurn(request: LLMToolGenerationRequest): Flow<LLMToolStreamEvent> =
        mDelegate.streamToolTurn(request.withToolSystemPrompt())

    private fun LLMToolGenerationRequest.withToolSystemPrompt(): LLMToolGenerationRequest =
        if (systemPromptApplied) this else copy(
            base = base.withSystemPrompt(),
            systemPromptApplied = true
        )

    /** 使用带模型系统指令的上下文执行一次性生成。 */
    override suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse {
        return mDelegate.generate(request.withSystemPrompt())
    }

    /** 使用同样的注入规则执行流式生成。 */
    override fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent> {
        return mDelegate.streamGenerate(request.withSystemPrompt())
    }

    /** 每次发送只创建请求副本，不修改调用方上下文，确保重试和复用不会累积指令。 */
    private fun LLMGenerationRequest.withSystemPrompt(): LLMGenerationRequest {
        if (mSystemPrompt.isBlank()) return this
        val instruction = LLMMessage(LLMMessageRole.System, mSystemPrompt)
        return copy(messages = listOf(instruction) + messages)
    }
}
