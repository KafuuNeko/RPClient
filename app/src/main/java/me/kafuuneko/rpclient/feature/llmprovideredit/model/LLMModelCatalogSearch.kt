package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.catalog.model.LLMAvailableModel

/** 在模型列表中按 ID 或显示名称进行大小写不敏感的搜索过滤。 */
internal fun List<LLMAvailableModel>.filterForSearch(
    query: String
): List<LLMAvailableModel> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    return filter { model ->
        model.id.contains(normalizedQuery, ignoreCase = true) ||
                model.displayName.contains(normalizedQuery, ignoreCase = true)
    }
}
