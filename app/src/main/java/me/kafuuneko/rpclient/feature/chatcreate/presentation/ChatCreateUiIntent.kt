package me.kafuuneko.rpclient.feature.chatcreate.presentation

sealed class ChatCreateUiIntent {
    data object Init : ChatCreateUiIntent()

    data object Back : ChatCreateUiIntent()

    data class SelectCharacter(val characterId: Long) : ChatCreateUiIntent()

    data class SelectFirstMessage(val index: Int) : ChatCreateUiIntent()

    data class ChangeTitle(val value: String) : ChatCreateUiIntent()

    data class ChangeUserNote(val value: String) : ChatCreateUiIntent()

    data class ToggleLorebookEntry(val entryId: Long) : ChatCreateUiIntent()

    data object CreateChat : ChatCreateUiIntent()
}
