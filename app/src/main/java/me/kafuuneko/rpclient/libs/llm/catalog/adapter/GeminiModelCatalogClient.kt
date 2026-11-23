package me.kafuuneko.rpclient.libs.llm.catalog.adapter

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
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

/**
 * Gemini 模型目录适配器。
 * - 两种协议共用分页与目录条目映射，各自保留版本路径及认证方式。
 * - Interactions 的模型目录可能只列出旧接口的方法名，保留能力未知的候选模型。
 */
class GeminiModelCatalogClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProviderConfig
) : LLMModelCatalogClient {
    /**
     * 查询所选协议的模型目录，分页令牌仅在本次请求中使用。
     * @return 按服务端顺序去重后的可用模型。
     */
    override suspend fun listModels(): List<LLMAvailableModel> {
        val models = mutableListOf<LLMAvailableModel>()
        var pageToken: String? = null
        val visitedTokens = mutableSetOf<String>()
        val interactions = mProvider.protocol == LLMProviderProtocol.GeminiInteractions
        val path = if (interactions) "/models" else "/v1beta/models"
        // 新协议的版本是预设地址的一部分，第三方网关前缀按原值保留。
        do {
            val url = "${mProvider.normalizedBaseUrl()}$path"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("pageSize", "1000")
                .apply {
                    if (!interactions && mProvider.apiKey.isNotBlank()) {
                        addQueryParameter("key", mProvider.apiKey)
                    }
                    pageToken?.let { text -> addQueryParameter("pageToken", text) }
                }
                .build()
            val request = Request.Builder()
                .url(url)
                .apply {
                    if (interactions && mProvider.apiKey.isNotBlank()) header(
                        "x-goog-api-key",
                        mProvider.apiKey
                    )
                }
                .applyProviderHeaders(mProvider)
                .build()
            val page = parseGeminiModelCatalogPage(mOkHttpClient.await(request), mProvider.protocol)
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
 * 按协议筛选 Gemini 模型页，保留服务端返回的显示名和上下文限制。
 *
 * 同时兼容官方 `supportedGenerationMethods` 与部分网关使用的 `supportedActions` 字段。
 * @param raw 服务端返回的单页 JSON。
 * @param protocol 当前模型配置采用的 Gemini 协议。
 * @return 可生成模型与下一页令牌。
 * @throws LLMModelCatalogInvalidResponseException 响应不是有效模型列表。
 */
internal fun parseGeminiModelCatalogPage(
    raw: String,
    protocol: LLMProviderProtocol = LLMProviderProtocol.Gemini
): GeminiModelCatalogPage {
    val json = parseCatalogJsonObject(raw)
    val data = json.arrayOrNull("models") ?: throw LLMModelCatalogInvalidResponseException()
    // 只过滤明确不可生成的型号，目录缺少新协议能力标记时仍允许用户试用。
    return GeminiModelCatalogPage(
        models = buildList {
            data.forEach { element ->
                val item = element.takeIf { element -> element.isJsonObject }?.asJsonObject
                    ?: return@forEach
                if (!item.supportsGeminiGeneration(protocol)) return@forEach
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

private fun JsonObject.supportsGeminiGeneration(protocol: LLMProviderProtocol): Boolean {
    val methods = arrayOrNull("supportedGenerationMethods")
        ?: arrayOrNull("supportedActions")
    if (protocol == LLMProviderProtocol.GeminiInteractions) {
        // 目录缺少新方法不表示不可用；仅排除明确只做嵌入等非生成任务的型号。
        val actions = methods?.toStringSet().orEmpty()
        return actions.isEmpty() || actions.any { it !in nonConversationalActions }
    }
    return methods?.containsString("generateContent") == true
}

private val nonConversationalActions =
    setOf("embedContent", "batchEmbedContents", "embedText", "countTokens", "countTextTokens")
