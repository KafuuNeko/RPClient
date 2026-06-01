package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.libs.room.entity.Lorebook

sealed class CharacterEditUiState {
    data object None : CharacterEditUiState()

    data class Normal(
        val mode: CharacterEditMode,
        val form: CharacterEditForm,
        val initialForm: CharacterEditForm = form,
        val loadState: CharacterEditLoadState = CharacterEditLoadState.None,
        val dialogState: CharacterEditDialogState = CharacterEditDialogState.None,
        val avatarFilePath: String? = null,
        val availableLorebooks: List<Lorebook> = emptyList()
    ) : CharacterEditUiState()

    data object Finished : CharacterEditUiState()
}

enum class CharacterEditMode {
    Create,
    Edit
}

sealed class CharacterEditLoadState {
    data object None : CharacterEditLoadState()
    data object Loading : CharacterEditLoadState()
    data object Saving : CharacterEditLoadState()
    data object Deleting : CharacterEditLoadState()
}

sealed class CharacterEditDialogState {
    data object None : CharacterEditDialogState()

    data class DeleteConfirm(
        val characterName: String
    ) : CharacterEditDialogState()

    data object UnsavedChangesConfirm : CharacterEditDialogState()
}
