package me.kafuuneko.rpclient.feature.worldbookentryedit.presentation

sealed class WorldBookEntryEditUiIntent {
    data class Init(val lorebookId: Long, val entryId: Long?) : WorldBookEntryEditUiIntent()

    data object Back : WorldBookEntryEditUiIntent()

    data class ChangeName(val value: String) : WorldBookEntryEditUiIntent()

    data object AddKeyword : WorldBookEntryEditUiIntent()

    data class ChangeKeyword(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteKeyword(val index: Int) : WorldBookEntryEditUiIntent()

    data object AddSecondaryKeyword : WorldBookEntryEditUiIntent()

    data class ChangeSecondaryKeyword(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteSecondaryKeyword(val index: Int) : WorldBookEntryEditUiIntent()

    data class ChangeConstant(val value: Boolean) : WorldBookEntryEditUiIntent()

    data object AddCategory : WorldBookEntryEditUiIntent()

    data class ChangeCategory(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteCategory(val index: Int) : WorldBookEntryEditUiIntent()

    data class ChangeOrder(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeDepth(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeContent(val value: String) : WorldBookEntryEditUiIntent()

    data object SaveEntry : WorldBookEntryEditUiIntent()

    data object DeleteEntryClick : WorldBookEntryEditUiIntent()

    data object ConfirmDeleteEntry : WorldBookEntryEditUiIntent()

    data object ConfirmDiscardChanges : WorldBookEntryEditUiIntent()

    data object DismissDialog : WorldBookEntryEditUiIntent()
}
