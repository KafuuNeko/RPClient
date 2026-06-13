package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.libs.prompt.PromptInspection

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
        val messages: List<ChatMessageUiModel>,
        val lorebookGroups: List<ChatLorebookGroupItem>,
        val isSessionLoreExpanded: Boolean = false,
        val inputDraft: String = "",
        val generationState: ChatGenerationState = ChatGenerationState.Idle,
        val streamEnabled: Boolean,
        val hasPromptInspection: Boolean = false,
        val expandedThinkBlockIds: Set<String> = emptySet(),
        val editingMessageId: String? = null,
        val editingMessageDraft: String = "",
        val dialogState: ChatDialogState = ChatDialogState.None
    ) : ChatUiState()

    data object Finished : ChatUiState()
}

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
}
