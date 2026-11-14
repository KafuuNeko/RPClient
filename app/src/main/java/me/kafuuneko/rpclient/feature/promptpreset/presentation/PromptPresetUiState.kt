package me.kafuuneko.rpclient.feature.promptpreset.presentation

import me.kafuuneko.rpclient.libs.prompt.PromptType

/** Prompt 预设页状态，编辑文本通过对话框子状态承载。 */
sealed class PromptPresetUiState {
    data object None : PromptPresetUiState()

    data class Normal(
        /** 当前页面各 Prompt 编辑字段的值映射。 */
        val promptValues: Map<PromptType, String>,
        val name: String = "",
        val description: String = "",
        val revision: Long = 1,
        val isBuiltin: Boolean = false,
        val busy: Boolean = false,
        val loadState: PromptPresetLoadState = PromptPresetLoadState.Ready,
        val browserState: PromptPresetBrowserState = PromptPresetBrowserState(),
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
    data class CopyTemplate(val name: String) :
        PromptPresetDialogState()

    data class Discard(val editor: EditPrompt) : PromptPresetDialogState()

    data class EditPrompt(
        /** 当前对象所属的业务类型。 */
        val type: PromptType,
        /** 当前编辑器中尚未提交的文本草稿。 */
        val draftText: String,
        /** 编辑器没有既有内容时使用的默认文本。 */
        val defaultText: String,
        /** 当前编辑字段允许插入的 Prompt 宏列表。 */
        val availableMacros: List<String>,
        val originalText: String = draftText
    ) : PromptPresetDialogState()
}

/** 详情加载状态与编辑弹窗相互独立。 */
enum class PromptPresetLoadState { Loading, Ready, Failed }
