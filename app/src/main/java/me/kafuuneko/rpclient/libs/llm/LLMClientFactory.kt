package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.llm.adapter.AnthropicMessagesLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAICompatibleLLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.prompt.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMTokenUsageRepository
import okhttp3.OkHttpClient

/**
 * 将持久化层的模型配置转换为对应协议的 [LLMClient]。
 *
 * 工厂负责选择协议适配器，并在最外层统一装配成功请求用量记录器。Prompt 后处理、
 * 模型配置选择和默认配置初始化仍由上层 Repository 处理，避免各适配器出现不同语义。
 */
class LLMClientFactory(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository,
    private val mLLMTokenUsageRepository: LLMTokenUsageRepository? = null,
    private val mPromptTokenizerRegistry: PromptTokenizerRegistry = PromptTokenizerRegistry()
) {
    /**
     * 根据模型配置创建协议适配器，并在统计仓库可用时附加 Token 用量记录器。
     */
    fun create(provider: LLMProviderConfig): LLMClient {
        val protocolClient = when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(
                mOkHttpClient,
                mLLMRequestLogRepository,
                provider
            )

            LLMProviderProtocol.Gemini -> GeminiLLMClient(
                mOkHttpClient,
                mLLMRequestLogRepository,
                provider
            )

            LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(
                mOkHttpClient,
                mLLMRequestLogRepository,
                provider
            )
        }
        val usageRepository = mLLMTokenUsageRepository ?: return protocolClient
        return LLMTokenUsageTrackingClient(
            mDelegate = protocolClient,
            mProvider = provider,
            mRepository = usageRepository,
            mTokenizerRegistry = mPromptTokenizerRegistry
        )
    }
}
