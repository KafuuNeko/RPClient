package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.llm.adapter.AnthropicMessagesLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAICompatibleLLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import okhttp3.OkHttpClient

/**
 * 将持久化层的供应商配置转换为对应协议的 [LLMClient]。
 *
 * 工厂只负责选择协议适配器，共享的 Prompt 后处理、供应商选择和默认配置初始化
 * 均由上层 Repository 处理，避免各适配器出现不同的业务语义。
 */
class LLMClientFactory(
    private val mOkHttpClient: OkHttpClient,
    private val mLLMRequestLogRepository: LLMRequestLogRepository
) {
    /**
     * 根据 Provider 协议创建具体适配器。
     */
    fun create(provider: LLMProviderConfig): LLMClient {
        return when (provider.protocol) {
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
    }
}
