package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem

sealed class ChatUiState {
    data object None : ChatUiState()

    data class Normal(
        val page: ChatPage = ChatPage.Conversation,
        val loadState: ChatLoadState = ChatLoadState.None,
        val dialogState: ChatDialogState = ChatDialogState.None,
        val session: ChatSessionItem,
        val character: ChatCharacterItem,
        val messages: List<ChatMessageUiModel>,
        val lorebookEntries: List<ChatLorebookEntryItem>,
        val isSessionLoreExpanded: Boolean = false,
        val inputDraft: String = "",
        val generationState: ChatGenerationState = ChatGenerationState.Idle,
        val streamEnabled: Boolean,
        val expandedThinkBlockIds: Set<String> = emptySet()
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
}

sealed class ChatDialogState {
    data object None : ChatDialogState()
    data class EditTitle(val text: String) : ChatDialogState()
    data class EditSummary(val text: String) : ChatDialogState()
    data class EditUserNote(val text: String) : ChatDialogState()
    data class EditCreatorNotes(val text: String) : ChatDialogState()
}
