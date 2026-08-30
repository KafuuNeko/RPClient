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
        val page: ChatPage = ChatPage.Conversation,
        val loadState: ChatLoadState = ChatLoadState.None,
        val session: ChatSessionItem,
        val character: ChatCharacterItem,
        val conversationState: ChatConversationState,
        val lorebookState: ChatLorebookState,
        val streamEnabled: Boolean,
        val hasPromptInspection: Boolean = false,
        val hasAvailableProvider: Boolean = true,
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
    val messages: List<ChatMessageUiModel>,
    val inputDraft: String = "",
    val generationState: ChatGenerationState = ChatGenerationState.Idle,
    val expandedThinkBlockIds: Set<String> = emptySet(),
    val editingMessageId: String? = null,
    val editingMessageDraft: String = ""
)

/** 单聊设置页中的世界书列表与搜索状态。 */
data class ChatLorebookState(
    val groups: List<ChatLorebookGroupItem>,
    val visibleGroups: List<ChatLorebookGroupItem> = groups,
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
        val query: String,
        val visibleGroups: List<ChatLorebookGroupItem>,
        val enabledEntryIds: Set<Long>
    ) : ChatDialogState()

    data object Exporting : ChatDialogState()

    data object Summarizing : ChatDialogState()

    data class PromptInspector(
        val inspection: PromptInspection
    ) : ChatDialogState()

    data class DeleteSessionConfirm(
        val sessionTitle: String
    ) : ChatDialogState()

    data class DeleteMessageConfirm(
        val messageId: String
    ) : ChatDialogState()

    data class ModelSettingsGuide(
        val title: String,
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
