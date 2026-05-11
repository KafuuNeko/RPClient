package me.kafuuneko.rpclient.feature.worldbookedit.presentation

import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm

sealed class WorldBookEditUiState {
    data object None : WorldBookEditUiState()

    data class Normal(
        val mode: WorldBookEditMode,
        val form: WorldBookEditForm,
        val initialForm: WorldBookEditForm = form,
        val loadState: WorldBookEditLoadState = WorldBookEditLoadState.None,
        val dialogState: WorldBookEditDialogState = WorldBookEditDialogState.None
    ) : WorldBookEditUiState()

    data object Finished : WorldBookEditUiState()
}

enum class WorldBookEditMode {
    Create,
    Edit
}

sealed class WorldBookEditLoadState {
    data object None : WorldBookEditLoadState()
    data object Loading : WorldBookEditLoadState()
    data object Saving : WorldBookEditLoadState()
    data object Deleting : WorldBookEditLoadState()
}

sealed class WorldBookEditDialogState {
    data object None : WorldBookEditDialogState()

    data class DeleteConfirm(
        val worldBookName: String
    ) : WorldBookEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEditDialogState()
}

