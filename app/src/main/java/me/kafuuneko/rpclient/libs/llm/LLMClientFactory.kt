package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.llm.adapter.AnthropicMessagesLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAICompatibleLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAIResponsesLLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.media.MessageImageRuntime
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import okhttp3.OkHttpClient

/**
 * 将持久化层的模型配置转换为对应协议的 [LLMClient]。
 *
 * 工厂负责选择协议适配器，并统一装配模型系统指令注入与成功请求用量记录器。
 * Prompt 后处理、模型配置选择和默认配置初始化仍由上层 Repository 处理。
 */
class LLMClientFactory(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mLLMTokenUsageRepository: LLMTokenUsageRepository? = null,
    private val mPromptTokenizerRegistry: PromptTokenizerRegistry = PromptTokenizerRegistry(),
    private val mImageRuntime: MessageImageRuntime? = null,
    private val mImageCapabilities: ImageInputCapabilityResolver = ImageInputCapabilityResolver()
) {
    /**
     * 根据模型配置创建协议适配器，统一前置系统指令，并按需记录实际发送内容的 Token 用量。
     *
     * @param provider 本次请求使用的模型配置
     * @return 已装配模型级请求处理的客户端
     */
    fun create(provider: LLMProviderConfig): LLMClient {
        // 先选择协议适配器，再按请求实际发送的内容装配统计与指令注入。
        val protocolClient = when (provider.protocol) {
            LLMProviderProtocol.OpenAIResponses -> OpenAIResponsesLLMClient(
                mOkHttpClient = mOkHttpClient,
                mLLMRequestLogRepository = mLLMRequestLogRepository,
                mProvider = provider,
                mImageRuntime = mImageRuntime,
                mImageCapabilities = mImageCapabilities,
            )

            LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(
                mOkHttpClient = mOkHttpClient,
                mLLMRequestLogRepository = mLLMRequestLogRepository,
                mProvider = provider,
                mImageRuntime = mImageRuntime,
                mImageCapabilities = mImageCapabilities,
            )

            LLMProviderProtocol.Gemini -> GeminiLLMClient(
                mOkHttpClient = mOkHttpClient,
                mLLMRequestLogRepository = mLLMRequestLogRepository,
                mProvider = provider,
                mImageRuntime = mImageRuntime,
                mImageCapabilities = mImageCapabilities,
            )

            LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(
                mOkHttpClient = mOkHttpClient,
                mLLMRequestLogRepository = mLLMRequestLogRepository,
                mProvider = provider,
                mImageRuntime = mImageRuntime,
                mImageCapabilities = mImageCapabilities,
            )
        }
        val usageRepository = mLLMTokenUsageRepository
        val trackedClient = if (usageRepository == null) {
            protocolClient
        } else {
            LLMTokenUsageTrackingClient(
                mDelegate = protocolClient,
                mProvider = provider,
                mRepository = usageRepository,
                mTokenizerRegistry = mPromptTokenizerRegistry,
            )
        }
        // 注入器必须位于统计器外层，使本地用量估算包含额外的系统指令。
        return LLMSystemPromptClient(trackedClient, provider.systemPrompt)
    }
}
