package me.kafuuneko.rpclient.feature.character.presentation

import me.kafuuneko.rpclient.feature.character.model.CharacterEditForm
import me.kafuuneko.rpclient.libs.room.entity.Character

sealed class CharacterUiState {
    data object None : CharacterUiState()

    data class Normal(
        val loadState: CharacterLoadState = CharacterLoadState.None,
        val dialogState: CharacterDialogState = CharacterDialogState.None,
        val searchText: String = "",
        val selectedCharacterId: Long? = null,
        val characters: List<Character> = emptyList(),
        val avatarFilePaths: Map<String, String> = emptyMap(),
        val editorState: CharacterEditorState = CharacterEditorState.None
    ) : CharacterUiState()

    data object Finished : CharacterUiState()
}

sealed class CharacterLoadState {
    data object None : CharacterLoadState()
    data object Loading : CharacterLoadState()
    data object Saving : CharacterLoadState()
    data object Deleting : CharacterLoadState()
}

sealed class CharacterDialogState {
    data object None : CharacterDialogState()

    data class DeleteConfirm(
        val characterId: Long,
        val characterName: String
    ) : CharacterDialogState()
}

sealed class CharacterEditorState {
    data object None : CharacterEditorState()

    data class Editing(
        val form: CharacterEditForm
    ) : CharacterEditorState()
}
