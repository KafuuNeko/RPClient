package me.kafuuneko.rpclient.feature.main.presentation

sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        val exampleText: String = ""
    ) : MainUiState()

    data object Finished : MainUiState()
}
