package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm

sealed class LLMProviderEditUiState {
    data object None : LLMProviderEditUiState()

    data class Normal(
        val mode: LLMProviderEditMode,
        val form: LLMProviderEditForm,
        val loadState: LLMProviderEditLoadState = LLMProviderEditLoadState.None,
        val testState: LLMProviderEditTestState = LLMProviderEditTestState.None
    ) : LLMProviderEditUiState()

    data object Finished : LLMProviderEditUiState()
}

enum class LLMProviderEditMode {
    Create,
    Edit
}

sealed class LLMProviderEditLoadState {
    data object None : LLMProviderEditLoadState()
    data object Saving : LLMProviderEditLoadState()
}

sealed class LLMProviderEditTestState {
    data object None : LLMProviderEditTestState()
    data object Testing : LLMProviderEditTestState()
    data class Success(val message: String) : LLMProviderEditTestState()
    data class Failed(val message: String) : LLMProviderEditTestState()
}
