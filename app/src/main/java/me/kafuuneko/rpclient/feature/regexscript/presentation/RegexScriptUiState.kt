package me.kafuuneko.rpclient.feature.regexscript.presentation

import me.kafuuneko.rpclient.feature.regexscript.model.RegexScriptDraft
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope

/** Regex 管理页的完整状态树。 */
sealed class RegexScriptUiState {
    /** 页面尚未完成首次加载。 */
    data object None : RegexScriptUiState()

    /**
     * 可交互页面状态。
     *
     * [scripts] 始终属于当前 [scope] 和已选角色；测试输入、编辑弹窗和授权状态也由
     * UiState 持有，Compose 不直接访问 Repository。
     */
    data class Normal(
        /** 正则脚本生效的业务作用域。 */
        val scope: RegexScriptScope = RegexScriptScope.Global,
        /** 当前页面或流程可使用的角色列表。 */
        val characters: List<RegexCharacterItem> = emptyList(),
        /** 当前选中角色的 ID。 */
        val selectedCharacterId: Long? = null,
        /** 当前页面或流程可使用的正则脚本列表。 */
        val scripts: List<RegexScript> = emptyList(),
        /** 当前角色是否已被授权执行关联脚本。 */
        val authorized: Boolean = true,
        /** 当前是否正在跨故事移动章节。 */
        val transferInProgress: Boolean = false,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: RegexScriptDialogState = RegexScriptDialogState.None,
        /** 正则测试区域当前使用的输入文本。 */
        val testInput: String = "",
        /** 执行当前正则脚本后得到的测试输出。 */
        val testOutput: String = "",
        /** 测试世界书条目时模拟的 Prompt 插入位置。 */
        val testPlacement: RegexPlacement = RegexPlacement.UserInput,
        /** 正则测试模拟的 Source、Markdown 或 Prompt 阶段。 */
        val testMode: RegexExecutionMode = RegexExecutionMode.Source
    ) : RegexScriptUiState()

    /** 页面业务已结束，Activity 收到后关闭。 */
    data class Finished(val previous: RegexScriptUiState) : RegexScriptUiState()

    companion object {
        fun finished(previous: RegexScriptUiState): RegexScriptUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 角色作用域选择器所需的最小角色信息。 */
data class RegexCharacterItem(
    val id: Long,
    val name: String
)

/** Regex 页面可见对话框状态。 */
sealed class RegexScriptDialogState {
    /** 当前没有对话框。 */
    data object None : RegexScriptDialogState()

    /** 脚本编辑器及其即时校验结果。 */
    data class Editor(
        val draft: RegexScriptDraft,
        val validationError: String? = null
    ) : RegexScriptDialogState()

    /** 删除脚本前的二次确认信息。 */
    data class DeleteConfirm(
        val scriptId: String,
        val scriptName: String
    ) : RegexScriptDialogState()
}
