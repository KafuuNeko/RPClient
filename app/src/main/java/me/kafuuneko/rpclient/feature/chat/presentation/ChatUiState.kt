package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.model.ModelSettingsGuideContent
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.ChatSettingsSnapshot
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection
import me.kafuuneko.rpclient.libs.room.model.PersonaSelection
import me.kafuuneko.rpclient.model.ChatWritingState
import me.kafuuneko.rpclient.model.MessageHistoryLoadState
import me.kafuuneko.rpclient.model.SummaryEditorUiState

/** 单聊页面状态树，覆盖会话加载、聊天、设置、编辑和对话框状态。 */
sealed class ChatUiState {
    /** 页面尚未接收初始化参数。 */
    data object None : ChatUiState()

    /** 会话参数已就绪，正在读取首次展示所需的数据。 */
    data object Loading : ChatUiState()

    /** 单聊页面稳定可渲染状态；所有 Compose 临时交互数据也集中保存在此。 */
    data class Normal(
        val imageState: MessageImageState = MessageImageState(),
        /** 当前页面正在展示的一级功能区域。 */
        val page: ChatPage = ChatPage.Conversation,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: ChatLoadState = ChatLoadState.None,
        /** 当前页面展示或编辑的会话数据。 */
        val session: ChatSessionItem,
        /** 设置页尚未保存的表单草稿与持久化基线。 */
        val settingsState: ChatSettingsState = ChatSettingsState(),
        /** 当前状态或操作关联的角色数据。 */
        val character: ChatCharacterItem,
        /** 单聊消息列表、输入和生成生命周期状态。 */
        val conversationState: ChatConversationState,
        /** 当前页面的世界书列表与选择状态。 */
        val lorebookState: ChatLorebookState,
        /** 当前会话是否启用流式生成。 */
        val streamEnabled: Boolean,
        /** 全局允许示例对话时才展示会话开关。 */
        val showCharacterExamplesSwitch: Boolean = true,
        /** 当前会话是否存在可供查看的 Prompt 明细。 */
        val hasPromptInspection: Boolean = false,
        /** 当前是否存在可用于生成的模型配置。 */
        val hasAvailableProvider: Boolean = true,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: ChatDialogState = ChatDialogState.None
    ) : ChatUiState()

    data class Finished(val previous: ChatUiState) : ChatUiState()

    companion object {
        fun finished(previous: ChatUiState): ChatUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 单聊设置表单草稿；摘要编辑历史随表单一起保存或放弃，世界书即时持久化。 */
data class ChatSettingsState(
    val titleDraft: String = "",
    val summary: SummaryEditorUiState = SummaryEditorUiState(),
    val userNoteDraft: String = "",
    val persona: PersonaSelection = PersonaSelection(),
    val userNameDraft: String = "",
    val userDescriptionDraft: String = "",
    val inheritedDescription: String = "",
    val creatorNotesDraft: String = "",
    val inheritedCreatorNotes: String = "",
    val includeCharacterExamples: Boolean = true,
    val autoSummaryPaused: Boolean = false,
    val savedSnapshot: ChatSettingsSnapshot? = null,
) {
    val summaryDraft: String get() = summary.text
    val canResummarize: Boolean get() = summary.canResummarize
}

/** 单聊消息列表、输入与生成生命周期子状态。 */
data class ChatConversationState(
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<ChatMessageUiModel>,
    /** 完整会话历史中是否存在角色回复，不受当前分页窗口限制。 */
    val hasAssistantMessage: Boolean,
    /** 最新消息为角色回复且非唯一开场白时才允许重生成最新回复。 */
    val canRegenerateLatestReply: Boolean = hasAssistantMessage,
    /** 当前已加载窗口之前是否仍有更早消息。 */
    val canLoadOlderMessages: Boolean = false,
    /** 当前历史窗口的加载方向，同一时间只允许一个分页任务。 */
    val historyLoadState: MessageHistoryLoadState = MessageHistoryLoadState.None,
    /** 消息输入框中尚未发送的草稿。 */
    val inputDraft: String = "",
    /** 仅在输入框代写期间存在，结束后不保留状态栏或思考内容。 */
    val writingState: ChatWritingState? = null,
    /** 当前模型生成任务的生命周期状态。 */
    val generationState: ChatGenerationState = ChatGenerationState.Idle,
    /** 当前已展开显示的推理块 ID 集合。 */
    val expandedThinkBlockIds: Set<String> = emptySet(),
    /** 当前正在编辑的消息 ID；未编辑时为空。 */
    val editingMessageId: String? = null,
    /** 当前消息编辑器中尚未保存的草稿。 */
    val editingMessageDraft: String = "",
    /** 搜索历史位置；非空时禁止流式更新抢回底部。 */
    val searchAnchorMessageId: String? = null,
    val canLoadNewerMessages: Boolean = false,
)

/** 单聊设置页与会话快捷弹窗共用的世界书分组状态。 */
data class ChatLorebookState(
    /** 当前本地可用的全部世界书分组列表。 */
    val groups: List<ChatLorebookGroupItem>,
    /** 当前会话已启用的世界书分组列表，供设置页直接展示与操作。 */
    val enabledGroups: List<ChatLorebookGroupItem> = groups.filter { it.enabledCount > 0 },
)

/** 单聊页面当前展示的一级页面。 */
enum class ChatPage {
    Conversation,

    Settings
}

/** 会话就绪后的数据库操作状态，不包含首次加载和模型生成。 */
sealed class ChatLoadState {
    data object None : ChatLoadState()

    data object Saving : ChatLoadState()

    data object Deleting : ChatLoadState()
}

/** 单聊页面互斥显示的业务对话框。 */
sealed class ChatDialogState {
    /** 代写失败后保留错误原因，直到用户关闭提示。 */
    data class WritingFailure(val message: String) : ChatDialogState()

    /** 摘要被输出额度截断，等待用户前往全局设置调整。 */
    data object SummaryTokenLimit : ChatDialogState()

    data object None : ChatDialogState()

    data class SessionLorebook(
        /** 当前列表或对话框使用的搜索关键词。 */
        val query: String,
        /** 按搜索条件过滤后实际展示的分组列表。 */
        val visibleGroups: List<ChatLorebookGroupItem>,
        /** 当前会话已启用的世界书条目 ID 集合。 */
        val enabledEntryIds: Set<Long>,
        /** 是否仅展示当前草稿中已启用的条目。 */
        val showSelectedOnly: Boolean = false,
    ) : ChatDialogState()

    /** 单聊设置页添加新世界书的可检索多选弹窗状态（仅展示当前未启用的世界书）。 */
    data class AddLorebooks(
        /** 当前尚未启用的全部候选世界书分组。 */
        val candidateGroups: List<ChatLorebookGroupItem>,
        /** 当前弹窗使用的搜索关键词。 */
        val query: String,
        /** 是否仅展示弹窗草稿中已勾选的条目。 */
        val showSelectedOnly: Boolean,
        /** 按搜索词与筛选条件过滤后当前展示的候选分组列表。 */
        val visibleGroups: List<ChatLorebookGroupItem>,
        /** 弹窗草稿中待添加的世界书条目 ID 集合。 */
        val selectedEntryIds: Set<Long>,
    ) : ChatDialogState()

    data object Exporting : ChatDialogState()

    data object Saving : ChatDialogState()

    data object UnsavedSettingsChanges : ChatDialogState()

    data object Summarizing : ChatDialogState()

    data class PromptInspector(
        /** 与实际请求一致、供 Prompt 检查器展示的构建明细。 */
        val inspection: PromptInspection
    ) : ChatDialogState()

    data class DeleteSessionConfirm(
        /** 删除确认等操作中展示的会话标题。 */
        val sessionTitle: String
    ) : ChatDialogState()

    data class DeleteMessageConfirm(
        /** 当前操作关联的消息 ID。 */
        val messageId: String
    ) : ChatDialogState()

    /** 模型回复生成失败弹窗，提供本次上下文重试或关闭决策。 */
    data class GenerationFailure(
        /** 弹窗标题。 */
        val title: String,
        /** 脱敏后的本地化失败原因正文。 */
        val message: String,
        /** 是否提供「重试」确认按钮；关闭弹窗即放弃本次上下文重试。 */
        val canRetry: Boolean = false,
    ) : ChatDialogState()

    data class ModelSettingsGuide(
        /** 供界面展示或持久化的标题。 */
        val title: String,
        /** 需要展示或传递的消息内容。 */
        val message: String
    ) : ChatDialogState()
}

/**
 * 文件选择器返回会触发页面刷新，刷新任务可能携带已经过期的导出对话框快照。
 * 导出任务结束后不再接受该快照，避免 loading 对话框被重新打开。
 */
internal fun ChatDialogState.resolveExportDialogState(
    isExportActive: Boolean,
): ChatDialogState {
    return if (this == ChatDialogState.Exporting && !isExportActive) {
        ChatDialogState.None
    } else {
        this
    }
}

/** 将公共模型配置引导转换为单聊页面的对话框状态。 */
internal fun ModelSettingsGuideContent.toChatDialogState(): ChatDialogState.ModelSettingsGuide {
    return ChatDialogState.ModelSettingsGuide(title = title, message = message)
}
