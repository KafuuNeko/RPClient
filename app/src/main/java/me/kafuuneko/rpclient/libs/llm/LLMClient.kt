package me.kafuuneko.rpclient.libs.llm

import kotlinx.coroutines.flow.Flow
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationOptions
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationResponse
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMStreamEvent

/**
 * LLM 通用调用接口。
 *
 * 所有在线模型供应商都必须适配到这个接口，业务层只依赖这里的非流式与流式两种能力。
 */
interface LLMClient {
    /**
     * 一次性生成。等待模型完成后返回完整文本。
     */
    suspend fun generate(request: LLMGenerationRequest): LLMGenerationResponse

    /**
     * 使用单条用户消息进行一次性生成，适合测试连接或简单问答。
     */
    suspend fun generate(
        message: String,
        model: String? = null,
        options: LLMGenerationOptions = LLMGenerationOptions()
    ): LLMGenerationResponse = generate(
        LLMGenerationRequest(
            messages = listOf(LLMMessage(role = LLMMessageRole.User, content = message)),
            model = model,
            options = options
        )
    )

    /**
     * 流式生成。持续返回增量文本片段，适合聊天页边生成边渲染。
     */
    fun streamGenerate(request: LLMGenerationRequest): Flow<LLMStreamEvent>
}
