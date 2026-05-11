package me.kafuuneko.rpclient.feature.worldbooklist.presentation

sealed class WorldBookListUiIntent {
    data object Init : WorldBookListUiIntent()

    data object Resume : WorldBookListUiIntent()

    data object Back : WorldBookListUiIntent()

    data object CreateWorldBook : WorldBookListUiIntent()

    data class EditWorldBook(val lorebookId: Long) : WorldBookListUiIntent()
}

