package me.kafuuneko.rpclient.feature.main.presentation

import me.kafuuneko.rpclient.feature.main.model.MainGenerationParameter
import me.kafuuneko.rpclient.feature.main.model.MainHomeItemSelection
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem

/** 应用首页状态树，组合最近内容、全局设置和批量操作对话框。 */
sealed class MainUiState {
    data object None : MainUiState()

    data class Normal(
        /** 当前导航或设置页选中的页面。 */
        val selectedPage: MainPage = MainPage.Home,
        /** 主页资源、最近项目与多选模式的状态。 */
        val homeState: MainHomeState,
        /** 当前页面设置区域的可渲染状态。 */
        val settingsState: MainSettingsState,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: MainDialogState = MainDialogState.None
    ) : MainUiState()

    data class Finished(val previous: MainUiState) : MainUiState()

    companion object {
        fun finished(previous: MainUiState): MainUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

internal fun MainUiState.Normal.mergeResumeRefresh(
    homeState: MainHomeState,
    settingsState: MainSettingsState
): MainUiState.Normal {
    return copy(
        homeState = homeState.preserveCollapsedGroupsFrom(this.homeState),
        settingsState = settingsState.copy(
            chatDataManagementState = this.settingsState.chatDataManagementState,
            summaryState = settingsState.summaryState.copy(
                selectedTab = this.settingsState.summaryState.selectedTab
            )
        )
    )
}

internal fun MainUiState.Normal.canOpenDialog(): Boolean {
    return dialogState == MainDialogState.None &&
        settingsState.chatDataManagementState == MainChatDataManagementState.Idle
}

/** 首页互斥显示的确认对话框。 */
sealed class MainDialogState {
    data object None : MainDialogState()

    data class DeleteSelectedItems(
        /** 当前对象或操作涉及的数量。 */
        val count: Int,
        /** 当前页面是否正在执行删除操作。 */
        val isDeleting: Boolean = false
    ) : MainDialogState()

    data class RenameItem(
        /** 当前操作关联的列表项。 */
        val item: MainHomeItemSelection,
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 当前页面是否正在执行保存操作。 */
        val isSaving: Boolean = false
    ) : MainDialogState()

    data class EditGenerationParameter(
        /** 当前操作对应的生成参数。 */
        val parameter: MainGenerationParameter,
        /** 当前编辑控件中尚未提交的值。 */
        val draftValue: String
    ) : MainDialogState()

    data class EditUserDescription(
        /** 当前编辑器中尚未提交的文本草稿。 */
        val draftText: String
    ) : MainDialogState()

    data class ImportChatCharacterSelection(
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 导入数据中记录的原始角色名称。 */
        val sourceCharacterName: String,
        /** 当前会话或分组包含的消息数量。 */
        val messageCount: Int,
        /** 当前列表或对话框使用的搜索关键词。 */
        val query: String,
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<MainImportCharacterItem>,
        /** 按搜索条件过滤后实际展示的角色列表。 */
        val visibleCharacters: List<MainImportCharacterItem>,
        /** 当前选中角色的 ID。 */
        val selectedCharacterId: Long?,
        /** 当前页面是否正在执行导入操作。 */
        val isImporting: Boolean = false
    ) : MainDialogState()
}

/** 首页底部导航对应的一级页面。 */
enum class MainPage {
    Home,
    Settings
}
