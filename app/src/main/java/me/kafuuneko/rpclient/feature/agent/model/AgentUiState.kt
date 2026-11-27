package me.kafuuneko.rpclient.feature.agent.model

import androidx.annotation.StringRes
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState

/**
 * 单聊与群聊共用的 Agent 面板状态树。
 * - 不包含原始工具参数、工具原始返回或协议续接载荷。
 * - 工具选择、运行限额与剧情状态弹窗均由本状态树统一驱动。
 */
data class AgentUiState(
    val enabled: Boolean = false,
    val settingsLoaded: Boolean = false,
    val tools: List<AgentToolItem> = emptyList(),
    val presets: List<AgentPresetItem> = emptyList(),
    val selectedPresetId: Long? = null,
    val memoryReadOnly: Boolean = false,
    val autoPromoteMemory: Boolean = false,
    val limits: AgentLimitDraft = AgentLimitDraft(),
    val settingsDialog: AgentSettingsDialog = AgentSettingsDialog.None,
    val scene: String = "{}",
    val sceneFormatted: String = "",
    val sceneRevision: Long = 0,
    val sceneHistory: List<AgentSceneRevisionItem> = emptyList(),
    val canUndoScene: Boolean = false,
    val tasks: List<AgentTaskItem> = emptyList(),
    val expandedRunIds: Set<String> = emptySet(),
    val question: AgentQuestionItem? = null,
    val answerDraft: String = "",
    @param:StringRes val errorRes: Int? = null,
)

/** Agent 设置页互斥弹出的子配置对话框状态。 */
sealed interface AgentSettingsDialog {
    data object None : AgentSettingsDialog

    /** 可用工具多选草稿弹窗，仅在用户确认时一次性保存。 */
    data class Tools(val selectedToolIds: Set<String>) : AgentSettingsDialog

    /** 运行限额与压缩水位编辑弹窗，校验失败时保留草稿并在弹窗内提示。 */
    data class Limits(
        val draft: AgentLimitDraft,
        @param:StringRes val errorRes: Int? = null,
    ) : AgentSettingsDialog

    /** 剧情状态快照、近期修订差异及撤销弹窗。 */
    data object Scene : AgentSettingsDialog
}

/** 内置工具的功能分组，用于工具选择弹窗内分组展示。 */
enum class AgentToolGroup {
    Lore,

    Chat,

    Memory,

    Scene,

    Utility,

    Interaction,

    Network,
}

/** 单个可选工具的展示元数据。 */
data class AgentToolItem(
    val id: String,
    @param:StringRes val labelRes: Int,
    val group: AgentToolGroup,
    val enabled: Boolean,
    val writesMemory: Boolean = false,
    val label: String? = null,
    val needsConfiguration: Boolean = false,
    val globallyDisabled: Boolean = false,
)

/** 可供会话绑定的 Agent 提示词预设项；[id] 为空表示跟随全局活动预设。 */
data class AgentPresetItem(val id: Long?, val name: String)

/** 最近修订的可读差异，不把工具原始参数作为界面数据。 */
data class AgentSceneRevisionItem(val revision: Long, val difference: String)

/** 单次运行成本与压缩水位的可编辑草稿。 */
data class AgentLimitDraft(
    val rounds: String = DEFAULT_ROUNDS.toString(),
    val calls: String = DEFAULT_CALLS.toString(),
    val requests: String = DEFAULT_REQUESTS.toString(),
    val tokens: String = DEFAULT_TOKENS.toString(),
    val seconds: String = DEFAULT_SECONDS.toString(),
    val watermark: String = DEFAULT_WATERMARK.toString(),
) {
    companion object {
        const val DEFAULT_ROUNDS = 5
        const val DEFAULT_CALLS = 20
        const val DEFAULT_REQUESTS = 8
        const val DEFAULT_TOKENS = 64_000
        const val DEFAULT_SECONDS = 180
        const val DEFAULT_WATERMARK = 80
    }
}

/** 单次 Agent 运行摘要，区分流式正文预览与可折叠思考过程。 */
data class AgentTaskItem(
    val id: String,
    val messageId: Long?,
    @param:StringRes val statusRes: Int,
    val active: Boolean,
    val waiting: Boolean,
    val modelRequests: Int,
    val toolCalls: Int,
    val tokens: Long,
    val steps: List<AgentStepItem>,
    val liveText: String = "",
    val reasoning: String = "",
    @param:StringRes val reasonRes: Int? = null,
    val characterId: Long? = null,
    val runState: AgentRunState = AgentRunState.Preparing,
)

/** 运行步骤类型。 */
enum class AgentStepKind {
    Model,

    Tools,

    Compaction,
}

/** 运行中的单个步骤摘要及该步内的工具调用明细。 */
data class AgentStepItem(
    @param:StringRes val kindRes: Int,
    val completed: Boolean,
    val kind: AgentStepKind = AgentStepKind.Model,
    val tokens: Long? = null,
    val toolCalls: List<AgentToolCallItem> = emptyList(),
)

/** 单次工具调用的脱敏展示状态，不包含参数或原始返回。 */
data class AgentToolCallItem(
    val id: String,
    val toolId: String,
    @param:StringRes val labelRes: Int,
    val status: AgentToolCallStatus,
    val writeDisposition: AgentWriteDisposition? = null,
    val label: String? = null,
)

/** 工具调用的可视化执行状态。 */
enum class AgentToolCallStatus {
    Running,

    Completed,

    Failed,

    WaitingForUser,

    Unknown,
}

/** 本地写入工具的提案或提交状态。 */
enum class AgentWriteDisposition {
    Committed,

    Proposed,
}

/** 等待用户处理的具体场景类型，避免把外部联网确认误显为记忆范围变更。 */
enum class AgentQuestionKind {
    Input,

    LocalWriteConfirmation,

    ExternalWriteConfirmation,

    ExternalReadConfirmation,
}

/** 等待用户输入或写入/外发确认时的交互数据。 */
data class AgentQuestionItem(
    val runId: String,
    val question: String,
    val options: List<String>,
    val confirmation: Boolean,
    val details: String,
    val kind: AgentQuestionKind = if (confirmation) {
        AgentQuestionKind.LocalWriteConfirmation
    } else {
        AgentQuestionKind.Input
    },
    val toolName: String? = null,
    val destinationHost: String? = null,
    val parameterLines: List<Pair<String, String>> = emptyList(),
)

/** Agent 控件只发送用户行为，持久化和权限收紧由协调器完成。 */
sealed interface AgentUiAction {
    data class ToggleEnabled(val enabled: Boolean) : AgentUiAction
    data class SelectPreset(val id: Long?) : AgentUiAction
    data class ToggleMemoryReadOnly(val enabled: Boolean) : AgentUiAction
    data class ToggleAutoPromote(val enabled: Boolean) : AgentUiAction
    data object OpenToolsDialog : AgentUiAction
    data class ToggleDraftTool(val id: String) : AgentUiAction
    data class SetAllDraftTools(val enabled: Boolean) : AgentUiAction
    data object SaveTools : AgentUiAction
    data object OpenLimitsDialog : AgentUiAction
    data class ChangeLimit(val field: AgentLimitField, val value: String) : AgentUiAction
    data object ResetDefaultLimits : AgentUiAction
    data object SaveLimits : AgentUiAction
    data object OpenSceneDialog : AgentUiAction
    data object UndoScene : AgentUiAction
    data object DismissSettingsDialog : AgentUiAction
    data class ToggleTask(val runId: String) : AgentUiAction
    data class ChangeAnswer(val value: String) : AgentUiAction
    data class SubmitAnswer(val confirmed: Boolean) : AgentUiAction
    data object Cancel : AgentUiAction
}

enum class AgentLimitField { Rounds, Calls, Requests, Tokens, Seconds, Watermark }
