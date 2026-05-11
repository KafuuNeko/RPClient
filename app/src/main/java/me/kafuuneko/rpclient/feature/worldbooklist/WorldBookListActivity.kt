package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.ui.WorldBookListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class WorldBookListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookListViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldBookListUiState.Finished) finish()
        }

        WorldBookListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(WorldBookListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(WorldBookListUiIntent.Resume)
    }
}

