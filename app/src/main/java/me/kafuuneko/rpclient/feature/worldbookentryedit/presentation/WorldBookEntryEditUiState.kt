package me.kafuuneko.rpclient.feature.worldbookentryedit.presentation

import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm

/** 世界书条目编辑页状态；initialForm 用于退出时判断未保存修改。 */
sealed class WorldBookEntryEditUiState {
    data object None : WorldBookEntryEditUiState()

    data class Normal(
        val mode: WorldBookEntryEditMode,
        val form: WorldBookEntryEditForm,
        val initialForm: WorldBookEntryEditForm = form,
        val loadState: WorldBookEntryEditLoadState = WorldBookEntryEditLoadState.None,
        val dialogState: WorldBookEntryEditDialogState = WorldBookEntryEditDialogState.None
    ) : WorldBookEntryEditUiState()

    data class Finished(val previous: WorldBookEntryEditUiState) : WorldBookEntryEditUiState()

    companion object {
        fun finished(previous: WorldBookEntryEditUiState): WorldBookEntryEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 世界书条目编辑页的创建或编辑模式。 */
enum class WorldBookEntryEditMode {
    Create,
    Edit
}

/** 条目读取、保存和删除操作的互斥状态。 */
sealed class WorldBookEntryEditLoadState {
    data object None : WorldBookEntryEditLoadState()
    data object Loading : WorldBookEntryEditLoadState()
    data object Saving : WorldBookEntryEditLoadState()
    data object Deleting : WorldBookEntryEditLoadState()
}

/** 世界书条目编辑页当前显示的确认对话框。 */
sealed class WorldBookEntryEditDialogState {
    data object None : WorldBookEntryEditDialogState()

    data class DeleteConfirm(
        val entryName: String
    ) : WorldBookEntryEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEntryEditDialogState()
}
