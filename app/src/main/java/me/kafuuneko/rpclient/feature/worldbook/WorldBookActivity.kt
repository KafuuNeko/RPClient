package me.kafuuneko.rpclient.feature.worldbook

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiIntent
import me.kafuuneko.rpclient.feature.worldbook.presentation.WorldBookUiState
import me.kafuuneko.rpclient.feature.worldbook.ui.WorldBookLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class WorldBookActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldBookUiState.Finished) finish()
        }

        WorldBookLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(WorldBookUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(WorldBookUiIntent.Resume)
    }
}

