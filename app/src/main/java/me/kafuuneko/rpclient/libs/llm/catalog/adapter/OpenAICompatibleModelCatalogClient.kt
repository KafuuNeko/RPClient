package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.adapter.applyProviderHeaders
import me.kafuuneko.rpclient.libs.llm.adapter.await
import me.kafuuneko.rpclient.libs.llm.adapter.normalizedBaseUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * OpenAI Compatible 模型目录适配器。
 *
 * 通用兼容服务只保证模型 ID；OpenRouter 的扩展字段会在存在时作为展示元数据保留。
 */
class OpenAICompatibleModelCatalogClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProviderConfig
) : LLMModelCatalogClient {
    override suspend fun listModels(): List<LLMAvailableModel> {
        val request = Request.Builder()
            .url("${mProvider.normalizedBaseUrl()}/models")
            .apply {
                if (mProvider.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${mProvider.apiKey}")
                }
            }
            .applyProviderHeaders(mProvider)
            .build()
        return parseOpenAIModelCatalog(
            raw = mOkHttpClient.await(request),
            includeExtendedMetadata = mProvider.providerType == LLMProviderType.OpenRouter
        )
    }
}

/**
 * 解析 OpenAI Compatible `/models` 响应。
 *
 * 只有已知提供扩展元数据的模型服务才读取上下文、输出上限和参数列表；普通兼容网关
 * 的同名非标准字段不会被误当成可靠能力声明。
 */
internal fun parseOpenAIModelCatalog(
    raw: String,
    includeExtendedMetadata: Boolean
): List<LLMAvailableModel> {
    val data = parseCatalogJsonObject(raw).arrayOrNull("data")
        ?: throw LLMModelCatalogInvalidResponseException()
    return buildList {
        data.forEach { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@forEach
            val id = item.stringOrNull("id") ?: return@forEach
            add(
                LLMAvailableModel(
                    id = id,
                    displayName = item.stringOrNull("name") ?: id,
                    description = item.stringOrNull("description"),
                    contextTokens = if (includeExtendedMetadata) {
                        item.positiveIntOrNull("context_length")
                    } else {
                        null
                    },
                    maxOutputTokens = if (includeExtendedMetadata) {
                        item.objectOrNull("top_provider")
                            ?.positiveIntOrNull("max_completion_tokens")
                    } else {
                        null
                    },
                    inputModalities = if (includeExtendedMetadata) item.objectOrNull("architecture")?.arrayOrNull("input_modalities")?.toStringSet() else null,
                    supportedParameters = if (includeExtendedMetadata) {
                        item.arrayOrNull("supported_parameters")
                            ?.toStringSet()
                            .orEmpty()
                    } else {
                        emptySet()
                    }
                )
            )
        }
    }
}
