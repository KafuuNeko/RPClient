package me.kafuuneko.rpclient.feature.main.presentation

sealed class MainUiIntent {
    data object Init : MainUiIntent()

    data object Resume : MainUiIntent()

    data object Back : MainUiIntent()

}