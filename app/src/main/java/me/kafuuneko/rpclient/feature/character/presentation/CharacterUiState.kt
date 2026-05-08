package me.kafuuneko.rpclient.feature.character.presentation

import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel

sealed class CharacterUiState {
    data object None : CharacterUiState()

    data class Normal(
        val searchText: String = "",
        val selectedCharacterId: String,
        val characters: List<RpCharacterUiModel>
    ) : CharacterUiState()

    data object Finished : CharacterUiState()
}

