package me.kafuuneko.rpclient.feature.promptpreset

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiIntent
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetUiState
import me.kafuuneko.rpclient.feature.promptpreset.presentation.PromptPresetViewEvent
import me.kafuuneko.rpclient.feature.promptpreset.ui.PromptPresetLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** Prompt 预设管理页面宿主。 */
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
            emit = { mViewModel.emit(this) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(PromptPresetUiIntent.Init)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is PromptPresetViewEvent.CopyText -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.prompt_preset_title), viewEvent.text))
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
