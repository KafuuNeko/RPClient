package me.kafuuneko.rpclient.feature.chat.presentation

/** 单聊页面可接收的全部用户意图和生命周期事件。 */
sealed class ChatUiIntent {
    data class Init(val sessionId: String?) : ChatUiIntent()

    data object Resume : ChatUiIntent()

    data object Back : ChatUiIntent()

    data object SendMessage : ChatUiIntent()

    data object StopGeneration : ChatUiIntent()

    data object RegenerateLast : ChatUiIntent()

    data object ContinueLast : ChatUiIntent()

    data object ImpersonateUser : ChatUiIntent()

    data class RegenerateFromMessage(val messageId: String) : ChatUiIntent()

    data class BranchFromMessage(val messageId: String) : ChatUiIntent()

    data object OpenSessionLore : ChatUiIntent()

    data object OpenWorldBookManager : ChatUiIntent()

    data object OpenCharacterEditor : ChatUiIntent()

    data class ChangeInputDraft(val value: String) : ChatUiIntent()

    data class ToggleSessionLoreEntry(val entryId: Long) : ChatUiIntent()

    data class ToggleSessionLorebook(val lorebookId: Long) : ChatUiIntent()

    data object OpenChatSettings : ChatUiIntent()

    data object OpenPromptInspector : ChatUiIntent()

    data object CloseChatSettings : ChatUiIntent()

    data object SummarizeNow : ChatUiIntent()

    data object RestorePreviousSummary : ChatUiIntent()

    data class ToggleAutoSummaryPaused(val paused: Boolean) : ChatUiIntent()

    data object CancelSummary : ChatUiIntent()

    data object DeleteSessionClick : ChatUiIntent()

    data object ConfirmDeleteSession : ChatUiIntent()

    data object DismissDialog : ChatUiIntent()

    data class SaveTitle(val value: String) : ChatUiIntent()

    data class SaveSummary(val value: String) : ChatUiIntent()

    data class SaveUserNote(val value: String) : ChatUiIntent()

    data class SaveUserName(val value: String) : ChatUiIntent()

    data class SaveUserDescription(val value: String) : ChatUiIntent()

    data class SaveCreatorNotes(val value: String) : ChatUiIntent()

    data class CopyMessage(val messageId: String) : ChatUiIntent()

    data class StartEditMessage(val messageId: String) : ChatUiIntent()

    data class ChangeEditingMessageDraft(val value: String) : ChatUiIntent()

    data object SaveEditingMessage : ChatUiIntent()

    data object CancelEditingMessage : ChatUiIntent()

    data class ToggleThinkBlock(val blockId: String) : ChatUiIntent()

    data class DeleteMessageClick(val messageId: String) : ChatUiIntent()

    data class ConfirmDeleteMessage(val messageId: String) : ChatUiIntent()
}
