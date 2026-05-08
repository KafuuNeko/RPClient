package me.kafuuneko.rpclient.feature.character

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.feature.character.ui.CharacterLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class CharacterActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<CharacterViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is CharacterUiState.Finished) finish()
        }

        CharacterLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(CharacterUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(CharacterUiIntent.Resume)
    }
}

