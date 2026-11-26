package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditRequestExtensionsState
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditImageQualityState
import me.kafuuneko.rpclient.libs.llm.adapter.readImageInputOptions
import me.kafuuneko.rpclient.libs.llm.image.ImageInputCapabilities
import me.kafuuneko.rpclient.libs.llm.model.ImageInputOptions
import me.kafuuneko.rpclient.libs.llm.adapter.readOpenRouterRoutingPreferences
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider
import me.kafuuneko.rpclient.utils.formatJsonPretty

/** 将持久化模型配置映射成不含密钥原文的编辑表单。 */
internal fun LLMProvider.toEditForm() = LLMProviderEditForm(
    id = id,
    createTime = createTime,
    name = name,
    providerType = providerType,
    protocol = protocol,
    baseUrl = baseUrl,
    hasExistingApiKey = apiKey.isNotBlank(),
    model = model,
    systemPrompt = systemPrompt,
    hasExistingCustomHeaders = customHeadersJson.isNotBlank(),
    customHeadersJson = formatJsonPretty(customHeadersJson),
    requestBodyPatchJson = formatJsonPretty(requestBodyPatchJson).ifBlank { "{}" },
    temperature = temperature.toString(),
    topP = topP.toString(),
    maxTokens = maxTokens.toString(),
    contextTokens = contextTokens.toString(),
    tokenEstimateReservePercent = tokenEstimateReservePercent,
    localTokenEstimatorType = localTokenEstimatorType,
    sendTemperature = sendTemperature,
    sendTopP = sendTopP,
    useServerReportedUsage = useServerReportedUsage,
    promptPostProcessingMode = PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode),
    imageInputSetting = imageInputSetting,
    toolCallingSetting = toolCallingSetting,
    imageTokenEstimatorType = imageTokenEstimatorType,
    isEnabled = isEnabled,
)

/** 提取高级面板中的路由和图片处理配置，避免 View 解析协议字段。 */
internal fun LLMProviderEditForm.toRequestExtensionsState():
    LLMProviderEditRequestExtensionsState {
    val routing = requestBodyPatchJson.readOpenRouterRoutingPreferences()
    return LLMProviderEditRequestExtensionsState(
        isOpenRouter = providerType == LLMProviderType.OpenRouter,
        usesPreferredProvider = routing.usesPreferredProvider,
        preferredProvider = routing.preferredProvider,
        allowFallbacks = routing.allowFallbacks,
        imageQuality = toImageQualityState(),
    )
}

private fun LLMProviderEditForm.toImageQualityState(): LLMProviderEditImageQualityState {
    val options =
        runCatching { requestBodyPatchJson.readImageInputOptions() }.getOrDefault(ImageInputOptions())
    return LLMProviderEditImageQualityState(
        selected = options.quality,
        options = ImageInputCapabilities.qualities(protocol, model, providerType),
    )
}
