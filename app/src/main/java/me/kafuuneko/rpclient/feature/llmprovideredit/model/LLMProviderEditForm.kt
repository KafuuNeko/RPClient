package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_CONTEXT_TOKENS
import me.kafuuneko.rpclient.libs.llm.model.DEFAULT_LLM_MAX_TOKENS
import me.kafuuneko.rpclient.libs.llm.adapter.hasValidOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.adapter.protectedRequestBodyPaths
import me.kafuuneko.rpclient.libs.llm.adapter.validateRequestBodyPatch
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.LocalTokenEstimatorType
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
    /** 当前记录或列表项的唯一标识。 */
    val id: Long = 0L,
    /** 记录创建时的时间戳，单位为毫秒。 */
    val createTime: Long = System.currentTimeMillis(),
    /** 供界面展示和业务识别的名称。 */
    val name: String = "",
    /** 模型配置所属的供应商类型。 */
    val providerType: LLMProviderType = LLMProviderType.Custom,
    /** 模型配置实际采用的通信协议。 */
    val protocol: LLMProviderProtocol = LLMProviderProtocol.OpenAICompatible,
    /** 模型服务 API 的基础地址。 */
    val baseUrl: String = "",
    /** 已保存配置中是否存在可继续保留的 API Key。 */
    val hasExistingApiKey: Boolean = false,
    /** API Key 保留、替换或清除的编辑状态。 */
    val apiKeyEditMode: CredentialEditMode = CredentialEditMode.KeepExisting,
    /** 当前配置或请求使用的模型名称。 */
    val model: String = "",
    /** 已保存配置中是否存在可继续保留的自定义请求头。 */
    val hasExistingCustomHeaders: Boolean = false,
    /** 自定义请求头保留、替换或清除的编辑状态。 */
    val customHeadersEditMode: CredentialEditMode = CredentialEditMode.KeepExisting,
    /** 用户配置的自定义请求头 JSON，仅在受控业务层解析。 */
    val customHeadersJson: String = "",
    /** 合并到协议请求体的高级 JSON Patch 配置。 */
    val requestBodyPatchJson: String = "{}",
    /** 控制模型输出随机性的温度参数。 */
    val temperature: String = "0.8",
    /** 限制模型候选词累计概率的 Top P 参数。 */
    val topP: String = "1.0",
    /** 单次生成允许返回的最大 Token 数。 */
    val maxTokens: String = DEFAULT_LLM_MAX_TOKENS.toString(),
    /** 模型输入与输出共享的上下文 Token 上限。 */
    val contextTokens: String = DEFAULT_LLM_CONTEXT_TOKENS.toString(),
    /** 本地 Token 估算额外应用的安全预留百分比。 */
    val tokenEstimateReservePercent: Int = DEFAULT_TOKEN_ESTIMATE_RESERVE_PERCENT,
    /** 本地 Token 估算器的选择策略。 */
    val localTokenEstimatorType: LocalTokenEstimatorType = LocalTokenEstimatorType.Automatic,
    /** 是否向模型服务发送 temperature 参数。 */
    val sendTemperature: Boolean = true,
    /** 是否向模型服务发送 top_p 参数。 */
    val sendTopP: Boolean = true,
    /** 是否优先采用模型服务上报的 Token 用量。 */
    val useServerReportedUsage: Boolean = false,
    /** Prompt 提交前采用的后处理模式。 */
    val promptPostProcessingMode: PromptPostProcessingMode = PromptPostProcessingMode.None,
    /** 当前记录或配置是否启用。 */
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
            localTokenEstimatorType = localTokenEstimatorType,
            sendTemperature = sendTemperature,
            sendTopP = sendTopP,
            useServerReportedUsage = useServerReportedUsage,
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
