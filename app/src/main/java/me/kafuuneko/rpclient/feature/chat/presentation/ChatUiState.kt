package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.libs.prompt.model.PromptInspection

/** 单聊页面状态树，覆盖会话加载、聊天、设置、编辑和对话框状态。 */
sealed class ChatUiState {
    /** 尚未解析会话参数或加载数据库。 */
    data object None : ChatUiState()

    /** 单聊页面稳定可渲染状态；所有 Compose 临时交互数据也集中保存在此。 */
    data class Normal(
        /** 当前页面正在展示的一级功能区域。 */
        val page: ChatPage = ChatPage.Conversation,
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: ChatLoadState = ChatLoadState.None,
        /** 当前页面展示或编辑的会话数据。 */
        val session: ChatSessionItem,
        /** 当前状态或操作关联的角色数据。 */
        val character: ChatCharacterItem,
        /** 单聊消息列表、输入和生成生命周期状态。 */
        val conversationState: ChatConversationState,
        /** 当前页面的世界书列表与选择状态。 */
        val lorebookState: ChatLorebookState,
        /** 当前会话是否启用流式生成。 */
        val streamEnabled: Boolean,
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

/** 单聊消息列表、输入与生成生命周期子状态。 */
data class ChatConversationState(
    /** 当前状态或请求包含的消息列表。 */
    val messages: List<ChatMessageUiModel>,
    /** 当前已加载窗口之前是否仍有更早消息。 */
    val canLoadOlderMessages: Boolean = false,
    /** 当前是否正在向列表头部加载更早消息。 */
    val isLoadingOlderMessages: Boolean = false,
    /** 消息输入框中尚未发送的草稿。 */
    val inputDraft: String = "",
    /** 当前模型生成任务的生命周期状态。 */
    val generationState: ChatGenerationState = ChatGenerationState.Idle,
    /** 当前已展开显示的推理块 ID 集合。 */
    val expandedThinkBlockIds: Set<String> = emptySet(),
    /** 当前正在编辑的消息 ID；未编辑时为空。 */
    val editingMessageId: String? = null,
    /** 当前消息编辑器中尚未保存的草稿。 */
    val editingMessageDraft: String = ""
)

/** 单聊设置页中的世界书列表与搜索状态。 */
data class ChatLorebookState(
    /** 当前页面或结果包含的分组列表。 */
    val groups: List<ChatLorebookGroupItem>,
    /** 按搜索条件过滤后实际展示的分组列表。 */
    val visibleGroups: List<ChatLorebookGroupItem> = groups,
    /** 当前列表或对话框使用的搜索关键词。 */
    val query: String = ""
)

/** 单聊页面当前展示的一级页面。 */
enum class ChatPage {
    Conversation,
    Settings
}

/** 会话级数据库操作的加载状态，不包含模型生成状态。 */
sealed class ChatLoadState {
    data object None : ChatLoadState()
    data object Loading : ChatLoadState()
    data object Saving : ChatLoadState()
    data object Deleting : ChatLoadState()
}

/** 单聊页面互斥显示的业务对话框。 */
sealed class ChatDialogState {
    data object None : ChatDialogState()

    data class SessionLorebook(
        /** 当前列表或对话框使用的搜索关键词。 */
        val query: String,
        /** 按搜索条件过滤后实际展示的分组列表。 */
        val visibleGroups: List<ChatLorebookGroupItem>,
        /** 当前会话已启用的世界书条目 ID 集合。 */
        val enabledEntryIds: Set<Long>
    ) : ChatDialogState()

    data object Exporting : ChatDialogState()

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
    isExportActive: Boolean
): ChatDialogState {
    return if (this == ChatDialogState.Exporting && !isExportActive) {
        ChatDialogState.None
    } else {
        this
    }
}
