package me.kafuuneko.rpclient.feature.prompttemplatelist.presentation

/** 管理页状态按列表与弹窗拆分，加载错误不伪装成空列表。 */
sealed class PromptTemplateListUiState {
    data object None : PromptTemplateListUiState()
    data class Normal(
        val contentState: PromptTemplateContentState = PromptTemplateContentState.Loading,
        val dialogState: PromptTemplateDialogState = PromptTemplateDialogState.None,
        val busy: Boolean = false,
    ) : PromptTemplateListUiState()

    data class Finished(val previous: PromptTemplateListUiState) : PromptTemplateListUiState()
}

/** 模板列表当前可见内容，查询词与结果共同更新。 */
sealed class PromptTemplateContentState {
    data object Loading : PromptTemplateContentState()
    data object Failed : PromptTemplateContentState()
    data class Ready(
        val items: List<PromptTemplateItem>,
        val activeName: String,
        val activeId: Long,
        val query: String = "",
    ) : PromptTemplateContentState()
}

/** 已映射显示名称与时间的卡片，不向 Compose 暴露数据库实体。 */
data class PromptTemplateItem(
    val id: Long,
    val name: String,
    val description: String,
    val isBuiltin: Boolean,
    val isActive: Boolean,
    val revision: Long,
    val updatedTime: String,
)

/** 管理页互斥弹窗；取消新建不会写入半成品模板。 */
sealed class PromptTemplateDialogState {
    data object None : PromptTemplateDialogState()
    data class Metadata(
        val sourceId: Long,
        val targetId: Long? = null,
        val revision: Long = 0,
        val name: String = "",
        val description: String = "",
    ) : PromptTemplateDialogState()

    data class Delete(val item: PromptTemplateItem) : PromptTemplateDialogState()
}
