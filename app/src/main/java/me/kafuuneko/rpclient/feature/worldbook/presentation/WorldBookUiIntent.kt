package me.kafuuneko.rpclient.feature.worldbook.presentation

sealed class WorldBookUiIntent {
    data object Init : WorldBookUiIntent()

    data object Resume : WorldBookUiIntent()

    data object Back : WorldBookUiIntent()

    data class SelectLoreBook(val loreBookId: String) : WorldBookUiIntent()

    data object CreateLoreBook : WorldBookUiIntent()

    data object CreateEntry : WorldBookUiIntent()
}
