package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel
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
    val activeCharacter: RpCharacterUiModel,
    val recentSessions: List<ChatSessionUiModel>,
    val totalCharacters: Int,
    val totalWorldBooks: Int
)

data class MainSettingsState(
    val selectedProviderId: String,
    val providers: List<LLMProvider>,
    val temperature: Float,
    val topP: Float,
    val maxTokens: Int,
    val contextTokens: Int,
    val localFirstEnabled: Boolean,
    val streamEnabled: Boolean
)
