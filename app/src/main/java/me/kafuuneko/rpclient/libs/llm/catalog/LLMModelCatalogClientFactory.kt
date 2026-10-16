package me.kafuuneko.rpclient.libs.llm.catalog

import me.kafuuneko.rpclient.libs.llm.catalog.adapter.AnthropicModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.catalog.adapter.GeminiModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.catalog.adapter.OpenAICompatibleModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import okhttp3.OkHttpClient

/** 根据模型配置的协议创建模型目录适配器。 */
class LLMModelCatalogClientFactory(
    private val mOkHttpClient: OkHttpClient
) {
    fun create(provider: LLMProviderConfig): LLMModelCatalogClient {
        return when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible,
            LLMProviderProtocol.OpenAIResponses -> {
                OpenAICompatibleModelCatalogClient(mOkHttpClient, provider)
            }

            LLMProviderProtocol.Gemini -> {
                GeminiModelCatalogClient(mOkHttpClient, provider)
            }

            LLMProviderProtocol.AnthropicMessages -> {
                AnthropicModelCatalogClient(mOkHttpClient, provider)
            }
        }
    }
}
