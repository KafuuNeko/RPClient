package me.kafuuneko.rpclient.feature.characteredit.presentation

import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterLorebookItem
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterProviderItem
import androidx.compose.ui.graphics.ImageBitmap

/** 角色创建/编辑页面状态树。 */
sealed class CharacterEditUiState {
    data object None : CharacterEditUiState()

    /**
     * 角色表单稳定状态。
     *
     * [initialForm] 用于离开页面时判断未保存变更；世界书和模型配置列表只提供绑定选择，
     * 具体配置内容仍由各自的独立管理页面维护。
     */
    data class Normal(
        /** 当前流程采用的处理模式。 */
        val mode: CharacterEditMode,
        /** 当前页面正在编辑的表单数据。 */
        val form: CharacterEditForm,
        /** 进入编辑页时保存的初始表单快照。 */
        val initialForm: CharacterEditForm = form,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: CharacterEditLoadState = CharacterEditLoadState.None,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: CharacterEditDialogState = CharacterEditDialogState.None,
        /** 已解码、可直接用于界面展示的头像图像。 */
        val avatarImage: ImageBitmap? = null,
        /** 当前页面允许选择的世界书列表。 */
        val availableLorebooks: List<CharacterLorebookItem> = emptyList(),
        /** 当前页面允许选择的模型配置列表。 */
        val availableProviders: List<CharacterProviderItem> = emptyList()
    ) : CharacterEditUiState()

    data class Finished(val previous: CharacterEditUiState) : CharacterEditUiState()

    companion object {
        fun finished(previous: CharacterEditUiState): CharacterEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
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

/** 角色编辑页当前显示的业务对话框。 */
sealed class CharacterEditDialogState {
    data object None : CharacterEditDialogState()

    data class DeleteConfirm(
        /** 关联角色的显示名称快照。 */
        val characterName: String
    ) : CharacterEditDialogState()

    data class DeleteWithLorebookConfirm(
        /** 关联角色的显示名称快照。 */
        val characterName: String,
        /** 关联世界书的唯一 ID。 */
        val lorebookId: Long,
        /** 关联世界书的显示名称。 */
        val lorebookName: String
    ) : CharacterEditDialogState()

    data class PromptEditor(
        /** 当前操作对应的可编辑字段。 */
        val field: CharacterPromptField,
        /** 当前编辑器中尚未提交的文本草稿。 */
        val draftText: String
    ) : CharacterEditDialogState()

    data object UnsavedChangesConfirm : CharacterEditDialogState()
}

/** 可通过全屏 Prompt 编辑器修改的角色字段。 */
sealed class CharacterPromptField {
    data object Description : CharacterPromptField()
    data object Personality : CharacterPromptField()
    data object Scenario : CharacterPromptField()
    data class FirstMessage(val index: Int) : CharacterPromptField()
    data object DialogueExamples : CharacterPromptField()
    data object SystemPrompt : CharacterPromptField()
    data object PostHistoryInstructions : CharacterPromptField()
    data object DepthPrompt : CharacterPromptField()
    data class AlternateGreeting(val index: Int) : CharacterPromptField()
}
