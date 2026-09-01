package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_GROK_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LocalTokenEstimatorType

/**
 * 常用模型配置预设模板，用于快速填入标准接入参数。
 */
enum class ProviderPreset(
    val displayName: String,
    val providerType: LLMProviderType,
    val protocol: LLMProviderProtocol,
    val baseUrl: String,
    val defaultModel: String,
    val defaultRequestBodyPatchJson: String,
    val defaultLocalTokenEstimatorType: LocalTokenEstimatorType = LocalTokenEstimatorType.Automatic,
    val defaultUseServerReportedUsage: Boolean = false
) {
    DeepSeek(
        displayName = "DeepSeek",
        providerType = LLMProviderType.DeepSeek,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        defaultRequestBodyPatchJson = DEFAULT_DEEPSEEK_REQUEST_BODY_PATCH_JSON,
        defaultLocalTokenEstimatorType = LocalTokenEstimatorType.Cl100kBase
    ),
    Gemini(
        displayName = "Google Gemini",
        providerType = LLMProviderType.Gemini,
        protocol = LLMProviderProtocol.Gemini,
        baseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.5-flash",
        defaultRequestBodyPatchJson = DEFAULT_GEMINI_REQUEST_BODY_PATCH_JSON,
        defaultLocalTokenEstimatorType = LocalTokenEstimatorType.O200kBase,
        defaultUseServerReportedUsage = true
    ),
    Claude(
        displayName = "Anthropic Claude",
        providerType = LLMProviderType.Claude,
        protocol = LLMProviderProtocol.AnthropicMessages,
        baseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-7-sonnet-latest",
        defaultRequestBodyPatchJson = DEFAULT_CLAUDE_REQUEST_BODY_PATCH_JSON,
        defaultLocalTokenEstimatorType = LocalTokenEstimatorType.Cl100kBase,
        defaultUseServerReportedUsage = true
    ),
    ChatGPT(
        displayName = "OpenAI",
        providerType = LLMProviderType.ChatGPT,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        defaultRequestBodyPatchJson = "{}",
        defaultUseServerReportedUsage = true
    ),
    OpenRouter(
        displayName = "OpenRouter",
        providerType = LLMProviderType.OpenRouter,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openrouter/auto",
        defaultRequestBodyPatchJson = DEFAULT_OPENROUTER_REQUEST_BODY_PATCH_JSON
    ),
    Grok(
        displayName = "xAI Grok",
        providerType = LLMProviderType.Grok,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-2-latest",
        defaultRequestBodyPatchJson = DEFAULT_GROK_REQUEST_BODY_PATCH_JSON,
        defaultLocalTokenEstimatorType = LocalTokenEstimatorType.O200kBase
    ),
    Custom(
        displayName = "Custom",
        providerType = LLMProviderType.Custom,
        protocol = LLMProviderProtocol.OpenAICompatible,
        baseUrl = "",
        defaultModel = "",
        defaultRequestBodyPatchJson = "{}"
    )
}
