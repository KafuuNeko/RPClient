package me.kafuuneko.rpclient.feature.promptpreset

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.feature.promptpreset.ui.PromptPresetLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class PromptPresetActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<PromptPresetViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is PromptPresetUiState.Finished) finish()
        }

        PromptPresetLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(PromptPresetUiIntent.Init)
    }
}
