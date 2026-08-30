package me.kafuuneko.rpclient.feature.llmproviderlist.presentation

import me.kafuuneko.rpclient.feature.llmproviderlist.model.LLMProviderListItem

/** 模型配置列表页状态。 */
sealed class LLMProviderListUiState {
    data object None : LLMProviderListUiState()

    data class Normal(
        /** 当前页面或设置允许选择的模型配置列表。 */
        val providers: List<LLMProviderListItem>,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: LLMProviderListLoadState = LLMProviderListLoadState.None,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: LLMProviderListDialogState = LLMProviderListDialogState.None
    ) : LLMProviderListUiState()

    data class Finished(val previous: LLMProviderListUiState) : LLMProviderListUiState()

    companion object {
        fun finished(previous: LLMProviderListUiState): LLMProviderListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 模型配置列表页当前显示的业务对话框。 */
sealed class LLMProviderListDialogState {
    data object None : LLMProviderListDialogState()

    data class DeleteProvider(
        /** 关联模型配置的数据库 ID。 */
        val providerId: Long,
        /** 请求发生时模型配置的显示名称快照。 */
        val providerName: String,
        /** 删除模型配置时会解除关联的角色数量。 */
        val associatedCharacterCount: Int,
        /** 当前页面是否正在执行删除操作。 */
        val isDeleting: Boolean = false
    ) : LLMProviderListDialogState()
}

/** 模型配置加载或启停更新状态。 */
sealed class LLMProviderListLoadState {
    data object None : LLMProviderListLoadState()
    data object Loading : LLMProviderListLoadState()
}
