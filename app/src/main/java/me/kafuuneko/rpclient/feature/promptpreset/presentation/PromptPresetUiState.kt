package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.feature.promptpreset.model.PromptType

/** Prompt 预设页状态，编辑文本通过对话框子状态承载。 */
sealed class PromptPresetUiState {
    data object None : PromptPresetUiState()

    data class Normal(
        /** 当前页面各 Prompt 编辑字段的值映射。 */
        val promptValues: Map<PromptType, String>,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: PromptPresetDialogState = PromptPresetDialogState.None
    ) : PromptPresetUiState()

    data class Finished(val previous: PromptPresetUiState) : PromptPresetUiState()

    companion object {
        fun finished(previous: PromptPresetUiState): PromptPresetUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** Prompt 预设页当前显示的对话框。 */
sealed class PromptPresetDialogState {
    data object None : PromptPresetDialogState()

    data class EditPrompt(
        /** 当前对象所属的业务类型。 */
        val type: PromptType,
        /** 当前编辑器中尚未提交的文本草稿。 */
        val draftText: String,
        /** 编辑器没有既有内容时使用的默认文本。 */
        val defaultText: String,
        /** 当前编辑字段允许插入的 Prompt 宏列表。 */
        val availableMacros: List<String>
    ) : PromptPresetDialogState()
}
