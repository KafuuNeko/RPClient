package me.kafuuneko.rpclient.libs.llm

import me.kafuuneko.rpclient.libs.llm.adapter.AnthropicMessagesLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.GeminiLLMClient
import me.kafuuneko.rpclient.libs.llm.adapter.OpenAICompatibleLLMClient
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import okhttp3.OkHttpClient

class LLMClientFactory(
    private val mOkHttpClient: OkHttpClient
) {
    /**
     * 根据 Provider 协议创建具体适配器。
     */
    fun create(provider: LLMProviderConfig): LLMClient {
        return when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible -> OpenAICompatibleLLMClient(mOkHttpClient, provider)
            LLMProviderProtocol.Gemini -> GeminiLLMClient(mOkHttpClient, provider)
            LLMProviderProtocol.AnthropicMessages -> AnthropicMessagesLLMClient(mOkHttpClient, provider)
        }
    }
}
