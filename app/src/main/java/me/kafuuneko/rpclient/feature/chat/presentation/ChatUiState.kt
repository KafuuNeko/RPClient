package me.kafuuneko.rpclient.feature.chat.presentation

import me.kafuuneko.rpclient.libs.model.ChatMessageUiModel
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel

sealed class ChatUiState {
    data object None : ChatUiState()

    data class Normal(
        val session: ChatSessionUiModel,
        val character: RpCharacterUiModel,
        val messages: List<ChatMessageUiModel>,
        val sessionLoreBooks: List<LoreBookUiModel>,
        val sessionLoreEntries: List<LoreEntryUiModel>,
        val isSessionLoreExpanded: Boolean = false,
        val inputDraft: String,
        val generationStatus: String
    ) : ChatUiState()

    data object Finished : ChatUiState()
}
