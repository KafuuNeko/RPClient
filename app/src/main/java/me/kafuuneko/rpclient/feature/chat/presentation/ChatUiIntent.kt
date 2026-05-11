package me.kafuuneko.rpclient.feature.chat.presentation

sealed class ChatUiIntent {
    data class Init(
        val sessionId: String?,
        val firstMessage: String?
    ) : ChatUiIntent()

    data object Resume : ChatUiIntent()

    data object Back : ChatUiIntent()

    data object SendMessage : ChatUiIntent()

    data object StopGeneration : ChatUiIntent()

    data object RegenerateLast : ChatUiIntent()

    data class RegenerateFromMessage(val messageId: String) : ChatUiIntent()

    data object OpenSessionLore : ChatUiIntent()

    data class ChangeInputDraft(val value: String) : ChatUiIntent()

    data class ToggleSessionLoreEntry(val entryId: Long) : ChatUiIntent()

    data object OpenChatSettings : ChatUiIntent()

    data object CloseChatSettings : ChatUiIntent()

    data object EditTitleClick : ChatUiIntent()

    data object EditSummaryClick : ChatUiIntent()

    data object EditUserNoteClick : ChatUiIntent()

    data object EditCreatorNotesClick : ChatUiIntent()

    data object SummarizeNow : ChatUiIntent()

    data class SaveTitle(val value: String) : ChatUiIntent()

    data class SaveSummary(val value: String) : ChatUiIntent()

    data class SaveUserNote(val value: String) : ChatUiIntent()

    data class SaveCreatorNotes(val value: String) : ChatUiIntent()

    data class ToggleThinkBlock(val blockId: String) : ChatUiIntent()

    data object DismissDialog : ChatUiIntent()
}
