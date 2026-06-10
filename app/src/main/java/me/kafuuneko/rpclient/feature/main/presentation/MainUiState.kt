package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.feature.main.model.MainGroupChatSessionItem
import me.kafuuneko.rpclient.libs.prompt.PromptPostProcessingMode
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        val selectedPage: MainPage = MainPage.Home,
        val homeState: MainHomeState,
        val settingsState: MainSettingsState,
        val dialogState: MainDialogState = MainDialogState.None
    ) : MainUiState()

    data object Finished : MainUiState()
}

sealed class MainDialogState {
    data object None : MainDialogState()
    data class DeleteSelectedSessions(
        val count: Int
    ) : MainDialogState()
}

enum class MainPage {
    Home,
    Settings
}

data class MainHomeState(
    val recentSessions: List<MainChatSessionItem>,
    val groupChatSessions: List<MainGroupChatSessionItem> = emptyList(),
    val totalCharacters: Int,
    val totalWorldBooks: Int,
    val multiSelectMode: Boolean = false,
    val selectedSessionIds: Set<String> = emptySet()
)

data class MainSettingsState(
    val userName: String,
    val userDescription: String,
    val selectedProviderId: String,
    val providers: List<LLMProvider>,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val contextTokens: Int,
    val localFirstEnabled: Boolean,
    val streamEnabled: Boolean,
    val promptPostProcessingMode: PromptPostProcessingMode,
    val includeThinkInContext: Boolean,
    val debugModeEnabled: Boolean,
    val autoSummaryEnabled: Boolean,
    val summaryTriggerMessageCount: Int,
    val summaryWordsLimit: Int,
    val summaryMaxMessagesPerRequest: Int,
    val summaryResponseTokens: Int
)
