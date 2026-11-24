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

    companion object {
        /** 避免重复包裹 Finished 造成退出阶段递归渲染。 */
        fun finished(previous: PromptTemplateListUiState): PromptTemplateListUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 模板列表当前可见内容。 */
sealed class PromptTemplateContentState {
    data object Loading : PromptTemplateContentState()
    data object Failed : PromptTemplateContentState()

    data class Ready(
        val items: List<PromptTemplateItem>,
    ) : PromptTemplateContentState()
}

/** 已映射显示名称、覆盖数量与时间的卡片，不向 Compose 暴露数据库实体。 */
data class PromptTemplateItem(
    val id: Long,
    val name: String,
    val description: String,
    val isBuiltin: Boolean,
    val isActive: Boolean,
    val revision: Long,
    val updatedTime: String,
    val overrideCount: Int = 0,
)

/** 元数据弹窗的业务语义，用于准确呈现标题、来源说明与确认行为。 */
enum class PromptTemplateMetadataMode {
    Create,
    Copy,
    Edit,
}

/** 管理页互斥弹窗；取消新建或复制不会写入半成品模板。 */
sealed class PromptTemplateDialogState {
    data object None : PromptTemplateDialogState()

    data class Metadata(
        val mode: PromptTemplateMetadataMode,
        val sourceId: Long,
        val sourceName: String = "",
        val targetId: Long? = null,
        val revision: Long = 0,
        val name: String = "",
        val description: String = "",
    ) : PromptTemplateDialogState()

    data class Delete(val item: PromptTemplateItem) : PromptTemplateDialogState()
}
