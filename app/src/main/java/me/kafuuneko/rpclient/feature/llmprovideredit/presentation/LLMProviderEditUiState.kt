package me.kafuuneko.rpclient.feature.llmprovideredit.presentation

import me.kafuuneko.rpclient.feature.llmprovideredit.model.LLMProviderEditForm

/** LLM Provider 创建/编辑页面状态树。 */
sealed class LLMProviderEditUiState {
    data object None : LLMProviderEditUiState()

    /** Provider 表单、连接测试和未保存确认的稳定页面状态。 */
    data class Normal(
        val mode: LLMProviderEditMode,
        val form: LLMProviderEditForm,
        val initialForm: LLMProviderEditForm = form,
        val loadState: LLMProviderEditLoadState = LLMProviderEditLoadState.None,
        val testState: LLMProviderEditTestState = LLMProviderEditTestState.None,
        val dialogState: LLMProviderEditDialogState = LLMProviderEditDialogState.None
    ) : LLMProviderEditUiState()

    data object Finished : LLMProviderEditUiState()
}

/** Provider 页面当前是新增还是编辑。 */
enum class LLMProviderEditMode {
    Create,
    Edit
}

/** Provider 保存操作状态。 */
sealed class LLMProviderEditLoadState {
    data object None : LLMProviderEditLoadState()
    data object Saving : LLMProviderEditLoadState()
}

/** 最小生成请求连接测试的生命周期与结果。 */
sealed class LLMProviderEditTestState {
    data object None : LLMProviderEditTestState()
    data object Testing : LLMProviderEditTestState()
    data class Success(val message: String) : LLMProviderEditTestState()
    data class Failed(val message: String) : LLMProviderEditTestState()
}

/** Provider 编辑页互斥显示的确认对话框。 */
sealed class LLMProviderEditDialogState {
    data object None : LLMProviderEditDialogState()
    data object UnsavedChangesConfirm : LLMProviderEditDialogState()
}
