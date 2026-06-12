package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.libs.prompt.PromptInspection

sealed class ChatUiState {
    data object None : ChatUiState()

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

enum class ChatPage {
    Conversation,
    Settings
}

sealed class ChatLoadState {
    data object None : ChatLoadState()
    data object Loading : ChatLoadState()
    data object Saving : ChatLoadState()
    data object Deleting : ChatLoadState()
}

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
