package me.kafuuneko.rpclient.feature.chat.presentation

sealed class ChatUiIntent {
    data class Init(val sessionId: String?) : ChatUiIntent()

    data object Resume : ChatUiIntent()

    data object Back : ChatUiIntent()

    data object SendMessage : ChatUiIntent()

    data object StopGeneration : ChatUiIntent()

    data object RegenerateLast : ChatUiIntent()

    data object OpenSessionLore : ChatUiIntent()

    data class ToggleSessionLoreBook(val loreBookId: String) : ChatUiIntent()

    data class ToggleSessionLoreEntry(val entryId: String) : ChatUiIntent()
}
