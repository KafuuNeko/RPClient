package me.kafuuneko.rpclient.feature.characterlist.presentation

import me.kafuuneko.rpclient.feature.characterlist.model.CharacterListItem

/** 角色列表页状态；Normal 只强引用当前可见项的缩略图。 */
sealed class CharacterListUiState {
    data object None : CharacterListUiState()

    data class Normal(
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: CharacterListLoadState = CharacterListLoadState.None,
        /** 用户当前输入的搜索文本。 */
        val searchText: String = "",
        /** 当前选中角色的 ID。 */
        val selectedCharacterId: Long? = null,
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<CharacterListItem> = emptyList(),
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: CharacterListDialogState = CharacterListDialogState.None
    ) : CharacterListUiState()

    data class Finished(val previous: CharacterListUiState) : CharacterListUiState()

    companion object {
        fun finished(previous: CharacterListUiState): CharacterListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 角色列表页对话框状态。 */
sealed class CharacterListDialogState {
    data object None : CharacterListDialogState()

    data class LowEmbeddedLorebookBudgetConfirm(
        /** 导入文件中声明且需要用户确认的较低 Token 预算。 */
        val importedTokenBudget: Int,
        /** 本次批量操作会影响的角色数量。 */
        val affectedCharacterCount: Int
    ) : CharacterListDialogState()

    data class BatchImportResult(
        /** 批量操作中已经成功处理的项目数量。 */
        val successCount: Int,
        /** 批量操作中处理失败的项目数量。 */
        val failureCount: Int
    ) : CharacterListDialogState()
}

/** 角色列表读取或导入期间的阻塞状态。 */
sealed class CharacterListLoadState {
    data object None : CharacterListLoadState()
    data object Loading : CharacterListLoadState()

    data class Importing(
        /** 当前批量操作所处的处理阶段。 */
        val stage: CharacterImportStage,
        /** 批量操作中已经完成处理的项目数量。 */
        val completedCount: Int,
        /** 当前查询或统计包含的总数量。 */
        val totalCount: Int
    ) : CharacterListLoadState()
}

/** 批量角色卡导入的当前处理阶段。 */
enum class CharacterImportStage {
    Reading,
    Saving
}
