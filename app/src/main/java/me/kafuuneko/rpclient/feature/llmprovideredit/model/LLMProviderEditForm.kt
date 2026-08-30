package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_CONTEXT_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_MAX_TOKENS
import me.kafuuneko.rpclient.libs.llm.adapter.hasValidOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.protectedRequestBodyPaths
import me.kafuuneko.rpclient.libs.llm.adapter.validateRequestBodyPatch
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.libs.room.entity.MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
import me.kafuuneko.rpclient.libs.room.entity.MIN_TOKEN_ESTIMATE_RESERVE_PERCENT

/**
 * 模型配置编辑页的可渲染表单。
 *
 * 密钥和自定义请求头只以“是否已配置”的形式出现，原文由 ViewModel 私有持有。
 */
data class LLMProviderEditForm(
    val id: Long = 0L,
    val createTime: Long = System.currentTimeMillis(),
    val name: String = "",
    val providerType: LLMProviderType = LLMProviderType.Custom,
    val protocol: LLMProviderProtocol = LLMProviderProtocol.OpenAICompatible,
    val baseUrl: String = "",
    val hasExistingApiKey: Boolean = false,
    val apiKeyEditMode: CredentialEditMode = CredentialEditMode.KeepExisting,
    val model: String = "",
    val hasExistingCustomHeaders: Boolean = false,
    val customHeadersEditMode: CredentialEditMode = CredentialEditMode.KeepExisting,
    val customHeadersJson: String = "",
    val requestBodyPatchJson: String = "{}",
    val temperature: String = "0.8",
    val topP: String = "1.0",
    val maxTokens: String = DEFAULT_LLM_MAX_TOKENS.toString(),
    val contextTokens: String = DEFAULT_LLM_CONTEXT_TOKENS.toString(),
    val tokenEstimateReservePercent: Int = DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT,
    val sendTemperature: Boolean = true,
    val sendTopP: Boolean = true,
    val requestStreamUsage: Boolean = false,
    val promptPostProcessingMode: PromptPostProcessingMode = PromptPostProcessingMode.None,
    val isEnabled: Boolean = true
) {
    /** 校验并转换表单；敏感鉴权字段由 ViewModel 在转换时显式提供。 */
    fun toProviderOrNull(
        apiKey: String = ""
    ): LLMProvider? {
        val parsedTemperature = temperature.toFloatOrNull() ?: return null
        val parsedTopP = topP.toFloatOrNull() ?: return null
        val parsedMaxTokens = maxTokens.toIntOrNull() ?: return null
        val parsedContextTokens = contextTokens.toIntOrNull() ?: return null
        val capabilities = LLMProviderCapabilities.forProtocol(protocol)
        if (parsedMaxTokens <= 0 || parsedContextTokens <= 0) return null
        if (parsedMaxTokens >= parsedContextTokens) return null
        if (tokenEstimateReservePercent !in
            MIN_TOKEN_ESTIMATE_RESERVE_PERCENT..MAX_TOKEN_ESTIMATE_RESERVE_PERCENT
        ) return null
        if (sendTemperature && parsedTemperature !in capabilities.temperatureRange) return null
        if (sendTopP && parsedTopP !in capabilities.topPRange) return null
        if (validateRequestBodyPatch(
                requestBodyPatchJson,
                protectedRequestBodyPaths(protocol)
            ).isFailure
        ) return null
        if (providerType == LLMProviderType.OpenRouter &&
            !requestBodyPatchJson.hasValidOpenRouterRoutingPreferences()
        ) return null
        return LLMProvider(
            id = id,
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            customHeadersJson = customHeadersJson.trim(),
            requestBodyPatchJson = requestBodyPatchJson.trim().ifBlank { "{}" },
            temperature = parsedTemperature,
            topP = parsedTopP,
            maxTokens = parsedMaxTokens,
            contextTokens = parsedContextTokens,
            tokenEstimateReservePercent = tokenEstimateReservePercent,
            sendTemperature = sendTemperature,
            sendTopP = sendTopP,
            requestStreamUsage = requestStreamUsage &&
                capabilities.supportsStreamUsageRequest,
            promptPostProcessingMode = promptPostProcessingMode.ordinal,
            isEnabled = isEnabled,
            createTime = createTime
        )
    }
}

/** 敏感字段的非敏感编辑状态；Replace 只表示 ViewModel 已持有一份确认值。 */
enum class CredentialEditMode {
    KeepExisting,
    Replace,
    Clear
}

/** 比较标准化字段，判断可渲染表单是否发生变化。 */
fun LLMProviderEditForm.hasUnsavedChangesFrom(initialForm: LLMProviderEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}

/** 生成用于未保存变更比较的标准化表单。 */
fun LLMProviderEditForm.toComparableForm(): LLMProviderEditForm {
    return copy(
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        temperature = temperature.trim(),
        topP = topP.trim(),
        maxTokens = maxTokens.trim(),
        contextTokens = contextTokens.trim(),
        customHeadersJson = customHeadersJson.trim(),
        requestBodyPatchJson = requestBodyPatchJson.trim().ifBlank { "{}" }
    )
}
