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
        val scope: RegexScriptScope = RegexScriptScope.Global,
        val characters: List<RegexCharacterItem> = emptyList(),
        val selectedCharacterId: Long? = null,
        val scripts: List<RegexScript> = emptyList(),
        val authorized: Boolean = true,
        val dialogState: RegexScriptDialogState = RegexScriptDialogState.None,
        val testInput: String = "",
        val testOutput: String = "",
        val testPlacement: RegexPlacement = RegexPlacement.UserInput,
        val testMode: RegexExecutionMode = RegexExecutionMode.Source
    ) : RegexScriptUiState()

    /** 页面业务已结束，Activity 收到后关闭。 */
    data object Finished : RegexScriptUiState()
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
