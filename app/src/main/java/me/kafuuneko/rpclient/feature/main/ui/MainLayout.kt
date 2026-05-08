package me.kafuuneko.rpclient.feature.main.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.feature.main.presentation.MainUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme

@Composable
fun MainLayout(
    uiState: MainUiState,
    emit: MainUiIntent.() -> Unit
) {
    BackHandler { MainUiIntent.Back.emit() }
    when (uiState) {
        MainUiState.None, MainUiState.Finished -> Unit
        is MainUiState.Normal -> Normal(uiState, emit)
    }
}

@Composable
private fun Normal(
    uiState: MainUiState.Normal,
    emit: MainUiIntent.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Text(text = uiState.exampleText)
    }
}

@Preview
@Composable
private fun MainLayoutPreview() {
    AppTheme(dynamicColor = false) {
        MainLayout(uiState = MainUiState.Normal("Preview"), emit = {})
    }
}