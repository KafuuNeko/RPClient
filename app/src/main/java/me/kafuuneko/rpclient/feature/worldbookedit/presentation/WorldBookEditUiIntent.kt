package me.kafuuneko.rpclient.feature.worldbookedit.presentation

sealed class WorldBookEditUiIntent {
    data class Init(val lorebookId: Long?) : WorldBookEditUiIntent()

    data object Resume : WorldBookEditUiIntent()

    data object Back : WorldBookEditUiIntent()

    data class ChangeName(val value: String) : WorldBookEditUiIntent()

    data object AddEntry : WorldBookEditUiIntent()

    data class EditEntry(val entryId: Long) : WorldBookEditUiIntent()

    data object SaveWorldBook : WorldBookEditUiIntent()

    data object DeleteWorldBookClick : WorldBookEditUiIntent()

    data object ConfirmDeleteWorldBook : WorldBookEditUiIntent()

    data object ConfirmDiscardChanges : WorldBookEditUiIntent()

    data object DismissDialog : WorldBookEditUiIntent()
}
