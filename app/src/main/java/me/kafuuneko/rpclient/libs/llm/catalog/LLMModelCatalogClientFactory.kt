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
    /**
     * 为指定模型配置匹配目录端点及认证方式。
     * @param provider 用户选定的服务地址、协议和认证配置。
     * @return 可查询该服务模型目录的客户端。
     */
    fun create(provider: LLMProviderConfig): LLMModelCatalogClient {
        return when (provider.protocol) {
            LLMProviderProtocol.OpenAICompatible,
            LLMProviderProtocol.OpenAIResponses -> {
                OpenAICompatibleModelCatalogClient(mOkHttpClient, provider)
            }

            LLMProviderProtocol.GeminiInteractions,
            LLMProviderProtocol.Gemini -> {
                GeminiModelCatalogClient(mOkHttpClient, provider)
            }

            LLMProviderProtocol.AnthropicMessages -> {
                AnthropicModelCatalogClient(mOkHttpClient, provider)
            }
        }
    }
}
