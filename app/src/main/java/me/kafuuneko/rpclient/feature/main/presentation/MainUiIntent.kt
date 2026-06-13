package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.prompt.SummaryInjectionPosition

/** 首页及全局设置页可接收的全部用户意图。 */
sealed class MainUiIntent {
    data object Init : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

    data class SelectPage(val page: MainPage) : MainUiIntent()

    data class OpenChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateChat : MainUiIntent()

    data class OpenGroupChat(val sessionId: String) : MainUiIntent()

    data object OpenCreateGroupChat : MainUiIntent()

    data object OpenCharacterManager : MainUiIntent()

    data object OpenWorldBookManager : MainUiIntent()

    data object OpenProviderManager : MainUiIntent()

    data class ChangeUserName(val value: String) : MainUiIntent()

    data class ChangeUserDescription(val value: String) : MainUiIntent()

    data class SelectProvider(val providerId: String) : MainUiIntent()

    data class ToggleStreamEnabled(val enabled: Boolean) : MainUiIntent()

    data class SelectPostProcessingMode(val mode: PromptPostProcessingMode) : MainUiIntent()

    data class ToggleIncludeThinkInContext(val enabled: Boolean) : MainUiIntent()

    data class ToggleDebugModeEnabled(val enabled: Boolean) : MainUiIntent()

    data class ToggleAutoSummaryEnabled(val enabled: Boolean) : MainUiIntent()

    data class ChangeSummaryTriggerMessageCount(val value: String) : MainUiIntent()

    data class ChangeSummaryWordsLimit(val value: String) : MainUiIntent()

    data class ChangeSummaryMaxMessagesPerRequest(val value: String) : MainUiIntent()

    data class ChangeSummaryResponseTokens(val value: String) : MainUiIntent()

    data class ChangeSummaryInjectionTemplate(val value: String) : MainUiIntent()

    data class SelectSummaryInjectionPosition(
        val position: SummaryInjectionPosition
    ) : MainUiIntent()

    data object OpenPromptPreset : MainUiIntent()

    data object OpenRegexScripts : MainUiIntent()

    data object OpenRequestLogs : MainUiIntent()

    data object OpenAbout : MainUiIntent()

    data class EnterMultiSelect(val sessionId: String) : MainUiIntent()

    data class ToggleSessionSelection(val sessionId: String) : MainUiIntent()

    data object ExitMultiSelect : MainUiIntent()

    data object ShowDeleteSelectedDialog : MainUiIntent()

    data object ConfirmDeleteSelected : MainUiIntent()

    data object DismissDialog : MainUiIntent()
}
