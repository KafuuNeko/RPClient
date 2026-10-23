package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import me.kafuuneko.rpclient.libs.llm.adapter.applyProviderHeaders
import me.kafuuneko.rpclient.libs.llm.adapter.await
import me.kafuuneko.rpclient.libs.llm.adapter.normalizedBaseUrl
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogClient
import me.kafuuneko.rpclient.libs.llm.catalog.LLMModelCatalogInvalidResponseException
import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Gemini 模型目录适配器，只返回支持 generateContent 的模型。 */
class GeminiModelCatalogClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProviderConfig
) : LLMModelCatalogClient {
    override suspend fun listModels(): List<LLMAvailableModel> {
        val models = mutableListOf<LLMAvailableModel>()
        var pageToken: String? = null
        val visitedTokens = mutableSetOf<String>()
        do {
            val url = "${mProvider.normalizedBaseUrl()}/v1beta/models"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("pageSize", "1000")
                .apply {
                    if (mProvider.apiKey.isNotBlank()) {
                        addQueryParameter("key", mProvider.apiKey)
                    }
                    pageToken?.let { text -> addQueryParameter("pageToken", text) }
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .applyProviderHeaders(mProvider)
                .build()
            val page = parseGeminiModelCatalogPage(mOkHttpClient.await(request))
            models += page.models
            pageToken = page.nextPageToken?.takeIf { visitedTokens.add(it) }
        } while (pageToken != null)
        return models.distinctBy { it.id }
    }
}

/** Gemini 单页模型结果；分页 token 只在当前一次目录查询中使用，不持久化。 */
internal data class GeminiModelCatalogPage(
    val models: List<LLMAvailableModel>,
    val nextPageToken: String?
)

/**
 * 解析 Gemini 模型页，仅保留明确声明支持 `generateContent` 的模型。
 *
 * 同时兼容官方 `supportedGenerationMethods` 与部分网关使用的 `supportedActions` 字段。
 */
internal fun parseGeminiModelCatalogPage(raw: String): GeminiModelCatalogPage {
    val json = parseCatalogJsonObject(raw)
    val data = json.arrayOrNull("models") ?: throw LLMModelCatalogInvalidResponseException()
    return GeminiModelCatalogPage(
        models = buildList {
            data.forEach { element ->
                val item = element.takeIf { element -> element.isJsonObject }?.asJsonObject
                    ?: return@forEach
                if (!item.supportsGeminiGeneration()) return@forEach
                val id = item.stringOrNull("name")
                    ?.removePrefix("models/") ?: return@forEach
                add(
                    element = LLMAvailableModel(
                        id = id,
                        displayName = item.stringOrNull("displayName") ?: id,
                        description = item.stringOrNull("description"),
                        contextTokens = item.positiveIntOrNull("inputTokenLimit"),
                        maxOutputTokens = item.positiveIntOrNull("outputTokenLimit"),
                    ),
                )
            }
        },
        nextPageToken = json.stringOrNull("nextPageToken"),
    )
}

private fun com.google.gson.JsonObject.supportsGeminiGeneration(): Boolean {
    val methods = arrayOrNull("supportedGenerationMethods")
        ?: arrayOrNull("supportedActions") ?: return false
    return methods.containsString("generateContent")
}
