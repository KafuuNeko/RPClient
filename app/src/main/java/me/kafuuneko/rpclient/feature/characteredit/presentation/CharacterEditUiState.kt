package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.libs.room.entity.Lorebook

/** 角色创建/编辑页面状态树。 */
sealed class CharacterEditUiState {
    data object None : CharacterEditUiState()

    /**
     * 角色表单稳定状态。
     *
     * [initialForm] 用于离开页面时判断未保存变更；[availableLorebooks] 只提供绑定选择，
     * 世界书内容仍由独立管理页面维护。
     */
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

/** 角色页面当前是新增还是编辑已有角色。 */
enum class CharacterEditMode {
    Create,
    Edit
}

/** 角色加载、保存与删除操作状态。 */
sealed class CharacterEditLoadState {
    data object None : CharacterEditLoadState()
    data object Loading : CharacterEditLoadState()
    data object Saving : CharacterEditLoadState()
    data object Deleting : CharacterEditLoadState()
}

/** 角色删除及未保存变更的确认对话框。 */
sealed class CharacterEditDialogState {
    data object None : CharacterEditDialogState()

    data class DeleteConfirm(
        val characterName: String
    ) : CharacterEditDialogState()

    data class DeleteWithLorebookConfirm(
        val characterName: String,
        val lorebookId: Long,
        val lorebookName: String
    ) : CharacterEditDialogState()

    data object UnsavedChangesConfirm : CharacterEditDialogState()
}
