package me.kafuuneko.rpclient.libs.llm.model

/**
 * 在线模型供应商类型，用于 UI 展示和统计归类。
 */
enum class LLMProviderType {
    ChatGPT,
    Gemini,
    Claude,
    DeepSeek,
    OpenRouter,
    Custom
}

/**
 * 供应商实际使用的 HTTP 协议。
 *
 * ChatGPT、DeepSeek、OpenRouter 以及大多数第三方网关都归入 OpenAICompatible。
 */
enum class LLMProviderProtocol {
    OpenAICompatible,
    Gemini,
    AnthropicMessages
}

/**
 * LLM 模块运行时使用的 Provider 配置
 */
data class LLMProviderConfig(
    val name: String,
    val providerType: LLMProviderType,
    val protocol: LLMProviderProtocol,
    val baseUrl: String,
    val apiKey: String = "",
    val model: String,
    val customHeadersJson: String = "",
    val temperature: Float = 0.8f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 1200,
    val contextTokens: Int = 8192
)

/**
 * 通用聊天消息角色，适配器会转换成各协议自己的角色名称。
 */
enum class LLMMessageRole {
    System,
    User,
    Assistant
}

/**
 * 通用聊天消息。
 */
data class LLMMessage(
    val role: LLMMessageRole,
    val content: String
)

/**
 * 通用生成参数。为空时使用当前 Provider 的默认配置。
 */
data class LLMGenerationOptions(
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val stop: List<String> = emptyList()
)

/**
 * 通用生成请求，非流式与流式接口共用同一个请求模型。
 */
data class LLMGenerationRequest(
    val messages: List<LLMMessage>,
    val model: String? = null,
    val options: LLMGenerationOptions = LLMGenerationOptions(),
    val includeReasoningInContent: Boolean = false
)

/**
 * Token 用量信息。不同供应商字段不完全一致，因此允许为空。
 */
data class LLMUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

/**
 * 一次性生成完成后的完整响应。
 */
data class LLMGenerationResponse(
    val content: String,
    val model: String,
    val provider: LLMProviderType,
    val usage: LLMUsage? = null,
    val rawResponse: String
)

/**
 * 流式生成事件。
 */
sealed class LLMStreamEvent {
    /**
     * 模型增量输出的文本片段。
     */
    data class Delta(
        val content: String,
        val rawChunk: String
    ) : LLMStreamEvent()

    /**
     * 供应商明确返回的完成事件。
     */
    data class Finished(
        val rawChunk: String? = null
    ) : LLMStreamEvent()
}
