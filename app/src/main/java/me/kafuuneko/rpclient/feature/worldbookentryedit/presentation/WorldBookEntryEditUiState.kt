package me.kafuuneko.rpclient.feature.worldbookentryedit.presentation

import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm

/** 世界书条目编辑页状态；initialForm 用于退出时判断未保存修改。 */
sealed class WorldBookEntryEditUiState {
    data object None : WorldBookEntryEditUiState()

    data class Normal(
        /** 当前流程采用的处理模式。 */
        val mode: WorldBookEntryEditMode,
        /** 当前页面正在编辑的表单数据。 */
        val form: WorldBookEntryEditForm,
        /** 进入编辑页时保存的初始表单快照。 */
        val initialForm: WorldBookEntryEditForm = form,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: WorldBookEntryEditLoadState = WorldBookEntryEditLoadState.None,
        /** 当前页面互斥展示的对话框状态。 */
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

/** 世界书条目编辑页当前显示的确认对话框或全屏编辑器。 */
sealed class WorldBookEntryEditDialogState {
    data object None : WorldBookEntryEditDialogState()

    data class DeleteConfirm(
        val entryName: String
    ) : WorldBookEntryEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEntryEditDialogState()

    /** 设定正文全屏专注编辑器对话框。 */
    data class PromptEditor(
        val draftText: String
    ) : WorldBookEntryEditDialogState()
}
