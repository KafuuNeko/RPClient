package me.kafuuneko.rpclient.feature.llmprovideredit.model

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderCapabilities
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.llm.model.PromptCacheMode
import me.kafuuneko.rpclient.libs.llm.model.PromptCacheTtl
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

/**
 * LLM Provider 编辑表单。
 *
 * 数值参数以字符串保留，避免 Compose 输入过程中被强制回退；只有全部参数合法且
 * `maxTokens < contextTokens` 时才能转换为 Room 实体。
 */
data class LLMProviderEditForm(
    val id: Long = 0L,
    val createTime: Long = System.currentTimeMillis(),
    val name: String = "",
    val providerType: LLMProviderType = LLMProviderType.Custom,
    val protocol: LLMProviderProtocol = LLMProviderProtocol.OpenAICompatible,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val customHeadersJson: String = "",
    val temperature: String = "0.8",
    val topP: String = "1.0",
    val maxTokens: String = "1200",
    val contextTokens: String = "8192",
    val sendTemperature: Boolean = true,
    val sendTopP: Boolean = true,
    val promptPostProcessingMode: PromptPostProcessingMode = PromptPostProcessingMode.None,
    val promptCacheMode: PromptCacheMode = PromptCacheMode.Off,
    val promptCacheTtl: PromptCacheTtl = PromptCacheTtl.FiveMinutes,
    val isEnabled: Boolean = true
) {
    companion object {
        /** 从持久化 Provider 恢复编辑表单。 */
        fun from(obj: LLMProvider) = LLMProviderEditForm(
            id = obj.id,
            createTime = obj.createTime,
            name = obj.name,
            providerType = obj.providerType,
            protocol = obj.protocol,
            baseUrl = obj.baseUrl,
            apiKey = obj.apiKey,
            model = obj.model,
            customHeadersJson = obj.customHeadersJson,
            temperature = obj.temperature.toString(),
            topP = obj.topP.toString(),
            maxTokens = obj.maxTokens.toString(),
            contextTokens = obj.contextTokens.toString(),
            sendTemperature = obj.sendTemperature,
            sendTopP = obj.sendTopP,
            promptPostProcessingMode = PromptPostProcessingMode.fromOrdinal(
                obj.promptPostProcessingMode
            ),
            promptCacheMode = PromptCacheMode.fromOrdinal(obj.promptCacheMode),
            promptCacheTtl = PromptCacheTtl.fromOrdinal(obj.promptCacheTtl),
            isEnabled = obj.isEnabled
        )
    }

    /** 校验并转换表单；任一必需数值无效时返回 null。 */
    fun toProviderOrNull(): LLMProvider? {
        val parsedTemperature = temperature.toFloatOrNull() ?: return null
        val parsedTopP = topP.toFloatOrNull() ?: return null
        val parsedMaxTokens = maxTokens.toIntOrNull() ?: return null
        val parsedContextTokens = contextTokens.toIntOrNull() ?: return null
        val capabilities = LLMProviderCapabilities.forProtocol(protocol)
        if (parsedMaxTokens <= 0 || parsedContextTokens <= 0) return null
        if (parsedMaxTokens >= parsedContextTokens) return null
        if (sendTemperature && parsedTemperature !in capabilities.temperatureRange) return null
        if (sendTopP && parsedTopP !in capabilities.topPRange) return null
        return LLMProvider(
            id = id,
            name = name.trim(),
            providerType = providerType,
            protocol = protocol,
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim(),
            model = model.trim(),
            customHeadersJson = customHeadersJson.trim(),
            temperature = parsedTemperature,
            topP = parsedTopP,
            maxTokens = parsedMaxTokens,
            contextTokens = parsedContextTokens,
            sendTemperature = sendTemperature,
            sendTopP = sendTopP,
            promptPostProcessingMode = promptPostProcessingMode.ordinal,
            promptCacheMode = promptCacheMode.ordinal,
            promptCacheTtl = promptCacheTtl.ordinal,
            isEnabled = isEnabled,
            createTime = createTime
        )
    }
}

/** 比较标准化字段，判断用户是否修改了 Provider。 */
fun LLMProviderEditForm.hasUnsavedChangesFrom(initialForm: LLMProviderEditForm): Boolean {
    return toComparableForm() != initialForm.toComparableForm()
}

/** 生成用于未保存变更比较的标准化表单。 */
fun LLMProviderEditForm.toComparableForm(): LLMProviderEditForm {
    return copy(
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        customHeadersJson = customHeadersJson.trim(),
        temperature = temperature.trim(),
        topP = topP.trim(),
        maxTokens = maxTokens.trim(),
        contextTokens = contextTokens.trim()
    )
}
