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

/** Anthropic Models API 适配器，按服务端游标读取全部可用模型。 */
class AnthropicModelCatalogClient(
    private val mOkHttpClient: OkHttpClient,
    private val mProvider: LLMProviderConfig
) : LLMModelCatalogClient {
    override suspend fun listModels(): List<LLMAvailableModel> {
        val models = mutableListOf<LLMAvailableModel>()
        var afterId: String? = null
        val visitedIds = mutableSetOf<String>()
        do {
            val url = "${mProvider.normalizedBaseUrl()}/v1/models"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("limit", "1000")
                .apply { afterId?.let { text -> addQueryParameter("after_id", text) } }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("x-api-key", mProvider.apiKey)
                .header("anthropic-version", "2023-06-01")
                .applyProviderHeaders(mProvider)
                .build()
            val page = parseAnthropicModelCatalogPage(mOkHttpClient.await(request))
            models += page.models
            afterId = page.nextAfterId?.takeIf { visitedIds.add(it) }
        } while (afterId != null)
        return models.distinctBy { it.id }
    }
}

/** Anthropic 单页模型结果；[nextAfterId] 为空表示服务端分页已经结束。 */
internal data class AnthropicModelCatalogPage(
    val models: List<LLMAvailableModel>,
    val nextAfterId: String?
)

/**
 * 解析 Anthropic 模型页并校验游标完整性。
 *
 * 服务端声明仍有下一页却未返回 `last_id` 时直接判为无效响应，避免调用方把截断目录
 * 误认为完整结果。
 */
internal fun parseAnthropicModelCatalogPage(raw: String): AnthropicModelCatalogPage {
    val json = parseCatalogJsonObject(raw)
    val data = json.arrayOrNull("data") ?: throw LLMModelCatalogInvalidResponseException()
    val models = buildList {
        data.forEach { element ->
            val item = element.takeIf { element -> element.isJsonObject }?.asJsonObject
                ?: return@forEach
            val id = item.stringOrNull("id") ?: return@forEach
            add(
                element = LLMAvailableModel(
                    id = id,
                    displayName = item.stringOrNull("display_name") ?: id,
                ),
            )
        }
    }
    val hasMore = runCatching { json.get("has_more")?.asBoolean }
        .getOrNull()
        ?: false
    val lastId = json.stringOrNull("last_id")
    if (hasMore && lastId == null) {
        throw LLMModelCatalogInvalidResponseException()
    }
    return AnthropicModelCatalogPage(
        models = models,
        nextAfterId = lastId.takeIf { hasMore },
    )
}
