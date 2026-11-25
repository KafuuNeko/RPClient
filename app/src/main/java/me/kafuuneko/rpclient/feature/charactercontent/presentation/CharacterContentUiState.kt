package me.kafuuneko.rpclient.feature.charactercontent.presentation

import me.kafuuneko.rpclient.feature.charactercontent.model.CharacterContentItem
import me.kafuuneko.rpclient.feature.charactercontent.model.CharacterContentItemSelection
import me.kafuuneko.rpclient.libs.character.CharacterContentKind

/** 角色页在首次读取、正常浏览和退出过渡之间切换。 */
sealed interface CharacterContentUiState {
    data object Loading : CharacterContentUiState
    data object LoadFailed : CharacterContentUiState
    data class Normal(
        val characterName: String,
        val list: CharacterContentListState,
        val dialogState: CharacterContentDialogState = CharacterContentDialogState.None,
    ) : CharacterContentUiState

    data class Finished(val previous: CharacterContentUiState) : CharacterContentUiState
}

/** 筛选只改变当前角色的可见条目，不需要再次读取数据库。 */
data class CharacterContentListState(
    val items: List<CharacterContentItem>,
    val filter: CharacterContentKind? = null,
    val selectionState: CharacterContentSelectionState = CharacterContentSelectionState.None,
) {
    val visibleItems = if (filter == null) items else items.filter { it.type == filter }

    /** 顶栏图标与批量切换共用同一判断，隐藏分类的选择不影响当前筛选结果。 */
    val allVisibleSelected: Boolean
        get() {
            val selection =
                selectionState as? CharacterContentSelectionState.Selecting ?: return false
            return visibleItems.isNotEmpty() && visibleItems.all { it.selection in selection.selectedItems }
        }
}

/** 浏览与多选互斥；取消全部勾选后仍保留多选模式，直到用户主动退出。 */
sealed interface CharacterContentSelectionState {
    data object None : CharacterContentSelectionState
    data class Selecting(val selectedItems: Set<CharacterContentItemSelection>) :
        CharacterContentSelectionState
}

/** 删除确认持有固定的目标集合，避免后续界面交互改变本次删除范围。 */
sealed interface CharacterContentDialogState {
    data object None : CharacterContentDialogState
    data class DeleteSelectedItems(
        val items: Set<CharacterContentItemSelection>,
        val isDeleting: Boolean = false,
    ) : CharacterContentDialogState
}
