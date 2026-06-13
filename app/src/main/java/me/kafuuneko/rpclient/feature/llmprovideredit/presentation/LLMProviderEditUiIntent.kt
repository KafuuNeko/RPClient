package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.libs.llm.model.LLMProviderProtocol
import me.kafuuneko.rpclient.libs.llm.model.LLMProviderType

/** LLM Provider 编辑页的字段变更、连接测试和保存意图。 */
sealed class LLMProviderEditUiIntent {
    data class Init(val providerId: Long?) : LLMProviderEditUiIntent()

    data object Back : LLMProviderEditUiIntent()

    data class ChangeName(val value: String) : LLMProviderEditUiIntent()

    data class ChangeProviderType(val value: LLMProviderType) : LLMProviderEditUiIntent()

    data class ChangeProtocol(val value: LLMProviderProtocol) : LLMProviderEditUiIntent()

    data class ChangeBaseUrl(val value: String) : LLMProviderEditUiIntent()

    data class ChangeApiKey(val value: String) : LLMProviderEditUiIntent()

    data class ChangeModel(val value: String) : LLMProviderEditUiIntent()

    data class ChangeCustomHeadersJson(val value: String) : LLMProviderEditUiIntent()

    data class ChangeTemperature(val value: String) : LLMProviderEditUiIntent()

    data class ChangeTopP(val value: String) : LLMProviderEditUiIntent()

    data class ChangeMaxTokens(val value: String) : LLMProviderEditUiIntent()

    data class ChangeContextTokens(val value: String) : LLMProviderEditUiIntent()

    data class ToggleEnabled(val value: Boolean) : LLMProviderEditUiIntent()

    data object SaveClick : LLMProviderEditUiIntent()

    data object TestClick : LLMProviderEditUiIntent()

    data object CancelTest : LLMProviderEditUiIntent()

    data object ConfirmDiscardChanges : LLMProviderEditUiIntent()

    data object DismissDialog : LLMProviderEditUiIntent()
}
