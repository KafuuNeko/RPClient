package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.libs.room.entity.Character

sealed class CharacterListUiState {
    data object None : CharacterListUiState()

    data class Normal(
        val loadState: CharacterListLoadState = CharacterListLoadState.None,
        val searchText: String = "",
        val selectedCharacterId: Long? = null,
        val characters: List<Character> = emptyList(),
        val avatarFilePaths: Map<String, String> = emptyMap()
    ) : CharacterListUiState()

    data object Finished : CharacterListUiState()
}

sealed class CharacterListLoadState {
    data object None : CharacterListLoadState()
    data object Loading : CharacterListLoadState()
}
