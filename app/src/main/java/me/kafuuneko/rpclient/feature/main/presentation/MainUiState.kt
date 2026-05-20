package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainChatSessionItem
import me.kafuuneko.rpclient.libs.room.entity.LLMProvider

sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        val selectedPage: MainPage = MainPage.Home,
        val homeState: MainHomeState,
        val settingsState: MainSettingsState
    ) : MainUiState()

    data object Finished : MainUiState()
}

enum class MainPage {
    Home,
    Settings
}

data class MainHomeState(
    val recentSessions: List<MainChatSessionItem>,
    val totalCharacters: Int,
    val totalWorldBooks: Int
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
    val debugModeEnabled: Boolean,
    val autoSummaryEnabled: Boolean,
    val summaryTriggerMessageCount: Int,
    val summaryWordsLimit: Int,
    val summaryMaxMessagesPerRequest: Int,
    val summaryResponseTokens: Int
)
