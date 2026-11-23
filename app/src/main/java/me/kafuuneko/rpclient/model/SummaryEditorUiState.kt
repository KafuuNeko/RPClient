package me.kafuuneko.rpclient.model

/** 摘要编辑区显示的正文、重做能力和未保存状态。 */
data class SummaryEditorUiState(
    val text: String = "",
    val canResummarize: Boolean = false,
    val hasChanges: Boolean = false,
)

/** 生成供界面显示的摘要状态，版本历史由 ViewModel 持有。 */
internal fun SummaryEditorState.toUiState(): SummaryEditorUiState = SummaryEditorUiState(
    text = text,
    canResummarize = canResummarize,
    hasChanges = hasChanges,
)
