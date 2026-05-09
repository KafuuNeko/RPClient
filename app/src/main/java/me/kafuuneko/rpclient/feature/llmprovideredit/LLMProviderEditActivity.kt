package me.kafuuneko.rpclient.feature.llmprovideredit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiIntent
import me.kafuuneko.rpclient.feature.llmprovideredit.presentation.LLMProviderEditUiState
import me.kafuuneko.rpclient.feature.llmprovideredit.ui.LLMProviderEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class LLMProviderEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<LLMProviderEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is LLMProviderEditUiState.Finished) finish()
        }

        LLMProviderEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(LLMProviderEditUiIntent.Init(providerId))
    }

    companion object {
        const val EXTRA_PROVIDER_ID = "extra_provider_id"
    }
}
