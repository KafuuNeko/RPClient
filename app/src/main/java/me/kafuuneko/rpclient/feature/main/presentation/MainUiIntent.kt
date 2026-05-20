package me.kafuuneko.rpclient.feature.main.presentation

sealed class MainUiIntent {
    data object Init : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

    data class SelectPage(val page: MainPage) : MainUiIntent()

    data class OpenChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateChat : MainUiIntent()

    data object OpenCharacterManager : MainUiIntent()

    data object OpenWorldBookManager : MainUiIntent()

    data object OpenProviderManager : MainUiIntent()

    data class ChangeUserName(val value: String) : MainUiIntent()

    data class ChangeUserDescription(val value: String) : MainUiIntent()

    data class SelectProvider(val providerId: String) : MainUiIntent()

    data class ToggleStreamEnabled(val enabled: Boolean) : MainUiIntent()

    data class ToggleDebugModeEnabled(val enabled: Boolean) : MainUiIntent()

    data class ToggleAutoSummaryEnabled(val enabled: Boolean) : MainUiIntent()

    data class ChangeSummaryTriggerMessageCount(val value: String) : MainUiIntent()

    data class ChangeSummaryWordsLimit(val value: String) : MainUiIntent()

    data class ChangeSummaryMaxMessagesPerRequest(val value: String) : MainUiIntent()

    data class ChangeSummaryResponseTokens(val value: String) : MainUiIntent()

    data object OpenPromptPreset : MainUiIntent()

    data object OpenRequestLogs : MainUiIntent()
}
