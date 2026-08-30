package me.kafuuneko.rpclient.libs.llm.adapter

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** OpenRouter 路由快捷配置；底层仍以通用请求体 Patch 为唯一数据源。 */
internal data class OpenRouterRoutingPreferences(
    /** OpenRouter 路由时优先选择的上游供应商。 */
    val preferredProvider: String = "",
    /** 首选上游不可用时是否允许 OpenRouter 回退。 */
    val allowFallbacks: Boolean = true,
    /** 高级路由 JSON 是否显式配置了供应商顺序。 */
    private val mHasProviderOrder: Boolean = false
) {
    val usesPreferredProvider: Boolean get() = mHasProviderOrder
}

/** 从通用请求体 Patch 提取 OpenRouter 页面需要展示的路由字段。 */
internal fun String.readOpenRouterRoutingPreferences(): OpenRouterRoutingPreferences {
    val root = runCatching { JsonParser.parseString(this).asJsonObject }.getOrNull()
        ?: return OpenRouterRoutingPreferences()
    val provider = root.get("provider")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?: return OpenRouterRoutingPreferences()
    val order = provider.get("order")
    val preferred = order
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.firstOrNull()
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
        ?.asString
        .orEmpty()
    val allowFallbacks = provider.get("allow_fallbacks")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
        ?.asBoolean
        ?: true
    return OpenRouterRoutingPreferences(
        preferredProvider = preferred,
        allowFallbacks = allowFallbacks,
        mHasProviderOrder = order?.isJsonArray == true
    )
}

/** 开关首选上游供应商快捷配置，同时保留 Patch 中其他未知字段。 */
internal fun String.withOpenRouterPreferredProviderEnabled(enabled: Boolean): String =
    updateOpenRouterPatch { provider ->
        if (enabled) {
            if (provider.get("order")?.takeIf { it.isJsonArray }?.asJsonArray.isNullOrEmpty()) {
                provider.add("order", JsonArray().apply { add("deepinfra") })
            }
            if (!provider.has("allow_fallbacks")) provider.addProperty("allow_fallbacks", true)
        } else {
            provider.remove("order")
            provider.remove("allow_fallbacks")
        }
    }

/** 更新首选上游供应商 slug，同时保留 Patch 中其他未知字段。 */
internal fun String.withOpenRouterPreferredProvider(value: String): String =
    updateOpenRouterPatch { provider ->
        provider.add("order", JsonArray().apply { add(value.trim()) })
    }

/** 更新 OpenRouter 回退开关，同时保留 Patch 中其他未知字段。 */
internal fun String.withOpenRouterFallbacks(allowed: Boolean): String =
    updateOpenRouterPatch { provider -> provider.addProperty("allow_fallbacks", allowed) }

/** 校验 OpenRouter 已知路由字段的类型和值，未知字段继续交给网关处理。 */
internal fun String.hasValidOpenRouterRoutingPreferences(): Boolean {
    val root = runCatching { JsonParser.parseString(this).asJsonObject }.getOrNull()
        ?: return false
    val providerElement = root.get("provider") ?: return true
    if (providerElement.isJsonNull) return true
    if (!providerElement.isJsonObject) return false
    val provider = providerElement.asJsonObject
    val order = provider.get("order")
    if (order != null && !order.isJsonNull) {
        if (!order.isJsonArray || order.asJsonArray.size() == 0) return false
        if (order.asJsonArray.any {
                !it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString.isBlank()
            }
        ) return false
    }
    val allowFallbacks = provider.get("allow_fallbacks")
    return allowFallbacks == null || allowFallbacks.isJsonNull ||
        allowFallbacks.isJsonPrimitive && allowFallbacks.asJsonPrimitive.isBoolean
}

private fun String.updateOpenRouterPatch(block: (JsonObject) -> Unit): String {
    val root = runCatching { JsonParser.parseString(this).asJsonObject.deepCopy() }
        .getOrElse { JsonObject() }
    val provider = root.get("provider")
        ?.takeIf { it.isJsonObject }
        ?.asJsonObject
        ?.deepCopy()
        ?: JsonObject()
    block(provider)
    if (provider.size() == 0) root.remove("provider") else root.add("provider", provider)
    return PATCH_GSON.toJson(root)
}

private fun JsonArray?.isNullOrEmpty(): Boolean = this == null || size() == 0

private val PATCH_GSON = GsonBuilder().setPrettyPrinting().create()
