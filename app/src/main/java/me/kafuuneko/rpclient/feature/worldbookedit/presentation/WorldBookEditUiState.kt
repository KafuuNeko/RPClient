package me.kafuuneko.rpclient.feature.worldbookedit.presentation

import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm

/** 世界书编辑页状态；initialForm 用于统一判断未保存修改。 */
sealed class WorldBookEditUiState {
    data object None : WorldBookEditUiState()

    data class Normal(
        val mode: WorldBookEditMode,
        val form: WorldBookEditForm,
        val initialForm: WorldBookEditForm = form,
        val loadState: WorldBookEditLoadState = WorldBookEditLoadState.None,
        val dialogState: WorldBookEditDialogState = WorldBookEditDialogState.None
    ) : WorldBookEditUiState()

    data class Finished(val previous: WorldBookEditUiState) : WorldBookEditUiState()

    companion object {
        fun finished(previous: WorldBookEditUiState): WorldBookEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 世界书编辑页的创建或编辑模式。 */
enum class WorldBookEditMode {
    Create,
    Edit
}

/** 世界书编辑页当前执行的持久化操作。 */
sealed class WorldBookEditLoadState {
    data object None : WorldBookEditLoadState()
    data object Loading : WorldBookEditLoadState()
    data object Saving : WorldBookEditLoadState()
    data object Deleting : WorldBookEditLoadState()
}

/** 世界书编辑页当前显示的确认对话框。 */
sealed class WorldBookEditDialogState {
    data object None : WorldBookEditDialogState()

    data class DeleteConfirm(
        val worldBookName: String
    ) : WorldBookEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEditDialogState()
}
