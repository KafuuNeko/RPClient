package me.kafuuneko.rpclient.feature.chatcreate.presentation

import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.libs.room.entity.Character

sealed class ChatCreateUiState {
    data object None : ChatCreateUiState()

    data class Normal(
        val loadState: ChatCreateLoadState = ChatCreateLoadState.None,
        val form: ChatCreateForm = ChatCreateForm(),
        val characters: List<Character> = emptyList(),
        val selectedCharacterFirstMessages: List<String> = emptyList(),
        val lorebookEntries: List<ChatCreateLorebookEntryItem> = emptyList()
    ) : ChatCreateUiState()

    data object Finished : ChatCreateUiState()
}

sealed class ChatCreateLoadState {
    data object None : ChatCreateLoadState()
    data object Loading : ChatCreateLoadState()
    data object Creating : ChatCreateLoadState()
}
