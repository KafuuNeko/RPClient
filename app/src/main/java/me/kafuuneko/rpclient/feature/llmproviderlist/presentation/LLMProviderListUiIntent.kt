package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

/** 模型供应商列表页的用户意图。 */
sealed class LLMProviderListUiIntent {
    data object Init : LLMProviderListUiIntent()

    data object Resume : LLMProviderListUiIntent()

    data object Back : LLMProviderListUiIntent()

    data object CreateProvider : LLMProviderListUiIntent()

    data class EditProvider(val providerId: String) : LLMProviderListUiIntent()

    data class ToggleProviderEnabled(
        val providerId: String,
        val isEnabled: Boolean
    ) : LLMProviderListUiIntent()
}
