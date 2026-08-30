package me.kafuuneko.rpclient.feature.llmprovideredit.model

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
    hasExistingCustomHeaders = customHeadersJson.isNotBlank(),
    customHeadersJson = formatJsonPretty(customHeadersJson),
    requestBodyPatchJson = formatJsonPretty(requestBodyPatchJson).ifBlank { "{}" },
    temperature = temperature.toString(),
    topP = topP.toString(),
    maxTokens = maxTokens.toString(),
    contextTokens = contextTokens.toString(),
    tokenEstimateReservePercent = tokenEstimateReservePercent,
    sendTemperature = sendTemperature,
    sendTopP = sendTopP,
    useServerReportedUsage = useServerReportedUsage,
    promptPostProcessingMode = PromptPostProcessingMode.fromOrdinal(promptPostProcessingMode),
    isEnabled = isEnabled
)
