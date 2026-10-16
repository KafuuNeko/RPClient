package me.kafuuneko.rpclient.feature.chat.model

import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/**
 * 构建好的 LLM 请求及元数据包装。
 *
 * @property provider 使用的 LLM 服务配置
 * @property request 组装好的请求体
 * @property inspection Prompt 检查报告
 * @property worldInfoStateJson 世界书时序激活状态快照 JSON
 */
internal data class BuiltGenerationRequest(
    /** 当前请求关联的模型供应商类型。 */
    val provider: LLMProvider,
    /** 经过业务层组装、准备提交给模型服务的请求。 */
    val request: LLMGenerationRequest,
    /** 与实际请求一致、供 Prompt 检查器展示的构建明细。 */
    val inspection: PromptInspection,
    /** 序列化后的世界书时序状态，需要随会话或故事持久化。 */
    val worldInfoStateJson: String
)
