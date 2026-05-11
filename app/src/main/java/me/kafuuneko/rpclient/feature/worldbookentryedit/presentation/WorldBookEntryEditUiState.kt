package me.kafuuneko.rpclient.feature.worldbookentryedit.presentation

import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm

sealed class WorldBookEntryEditUiState {
    data object None : WorldBookEntryEditUiState()

    data class Normal(
        val mode: WorldBookEntryEditMode,
        val form: WorldBookEntryEditForm,
        val initialForm: WorldBookEntryEditForm = form,
        val loadState: WorldBookEntryEditLoadState = WorldBookEntryEditLoadState.None,
        val dialogState: WorldBookEntryEditDialogState = WorldBookEntryEditDialogState.None
    ) : WorldBookEntryEditUiState()

    data object Finished : WorldBookEntryEditUiState()
}

enum class WorldBookEntryEditMode {
    Create,
    Edit
}

sealed class WorldBookEntryEditLoadState {
    data object None : WorldBookEntryEditLoadState()
    data object Loading : WorldBookEntryEditLoadState()
    data object Saving : WorldBookEntryEditLoadState()
    data object Deleting : WorldBookEntryEditLoadState()
}

sealed class WorldBookEntryEditDialogState {
    data object None : WorldBookEntryEditDialogState()

    data class DeleteConfirm(
        val entryName: String
    ) : WorldBookEntryEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEntryEditDialogState()
}

