package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.ProviderPreset
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType
import me.kafuuneko.rpclient.libs.prompt.model.PromptPostProcessingMode

/** 模型配置编辑页的字段变更、连接测试和保存意图。 */
sealed class LLMProviderEditUiIntent {
    data class Init(val providerId: Long?) : LLMProviderEditUiIntent()

    data object Back : LLMProviderEditUiIntent()

    data class ApplyPresetTemplate(val preset: ProviderPreset) : LLMProviderEditUiIntent()

    data class ChangeName(val value: String) : LLMProviderEditUiIntent()

    data class ChangeProviderType(val value: LLMProviderType) : LLMProviderEditUiIntent()

    data class ChangeProtocol(val value: LLMProviderProtocol) : LLMProviderEditUiIntent()

    data class ChangeBaseUrl(val value: String) : LLMProviderEditUiIntent()

    data object ShowApiKeyEditor : LLMProviderEditUiIntent()

    data class ConfirmApiKeyReplacement(val value: String) : LLMProviderEditUiIntent()

    data object ClearApiKey : LLMProviderEditUiIntent()

    data object KeepExistingApiKey : LLMProviderEditUiIntent()

    data class ChangeModel(val value: String) : LLMProviderEditUiIntent()

    data object QueryModels : LLMProviderEditUiIntent()

    data object CancelModelQuery : LLMProviderEditUiIntent()

    data object ShowModelPicker : LLMProviderEditUiIntent()

    data class ChangeModelSearch(val value: String) : LLMProviderEditUiIntent()

    data class SelectAvailableModel(val modelId: String) : LLMProviderEditUiIntent()

    data object ShowCustomHeadersEditor : LLMProviderEditUiIntent()

    data class ConfirmCustomHeadersReplacement(val value: String) : LLMProviderEditUiIntent()

    data object ClearCustomHeaders : LLMProviderEditUiIntent()

    data object KeepExistingCustomHeaders : LLMProviderEditUiIntent()

    data object ShowRequestBodyPatchDialog : LLMProviderEditUiIntent()

    data class ConfirmRequestBodyPatch(val value: String) : LLMProviderEditUiIntent()

    data class ToggleOpenRouterPreferredProvider(val value: Boolean) : LLMProviderEditUiIntent()

    data class ChangeOpenRouterPreferredProvider(val value: String) : LLMProviderEditUiIntent()

    data class ToggleOpenRouterFallbacks(val value: Boolean) : LLMProviderEditUiIntent()

    data class ChangeTemperature(val value: String) : LLMProviderEditUiIntent()

    data class ChangeTopP(val value: String) : LLMProviderEditUiIntent()

    data class ChangeMaxTokens(val value: String) : LLMProviderEditUiIntent()

    data class ChangeContextTokens(val value: String) : LLMProviderEditUiIntent()

    data class ChangeTokenEstimateReservePercent(val value: Int) : LLMProviderEditUiIntent()

    data class ToggleSendTemperature(val value: Boolean) : LLMProviderEditUiIntent()

    data class ToggleSendTopP(val value: Boolean) : LLMProviderEditUiIntent()

    data class ToggleUseServerReportedUsage(val value: Boolean) : LLMProviderEditUiIntent()

    data class SelectPostProcessingMode(
        val value: PromptPostProcessingMode
    ) : LLMProviderEditUiIntent()

    data class ToggleEnabled(val value: Boolean) : LLMProviderEditUiIntent()

    data object SaveClick : LLMProviderEditUiIntent()

    data object TestClick : LLMProviderEditUiIntent()

    data object CancelTest : LLMProviderEditUiIntent()

    data object ConfirmDiscardChanges : LLMProviderEditUiIntent()

    data object DismissDialog : LLMProviderEditUiIntent()
}
