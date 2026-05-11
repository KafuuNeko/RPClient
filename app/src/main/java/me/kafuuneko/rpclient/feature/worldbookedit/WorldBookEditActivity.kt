package me.kafuuneko.rpclient.feature.worldbookedit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiState
import me.kafuuneko.rpclient.feature.worldbookedit.ui.WorldBookEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class WorldBookEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldBookEditUiState.Finished) finish()
        }

        WorldBookEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lorebookId = intent.getLongExtra(EXTRA_LOREBOOK_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(WorldBookEditUiIntent.Init(lorebookId))
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(WorldBookEditUiIntent.Resume)
    }

    companion object {
        const val EXTRA_LOREBOOK_ID = "extra_lorebook_id"
    }
}
