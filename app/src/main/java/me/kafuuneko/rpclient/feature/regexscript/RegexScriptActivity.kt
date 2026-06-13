package me.kafuuneko.rpclient.feature.regexscript

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiIntent
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiState
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptViewEvent
import me.kafuuneko.rpclient.feature.regexscript.ui.RegexScriptLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

class RegexScriptActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<RegexScriptViewModel>()

    private val mImporter = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { mViewModel.emit(RegexScriptUiIntent.ImportJson(it)) }
    }

    private val mExporter = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { mViewModel.emit(RegexScriptUiIntent.ExportJson(it)) }
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val state by mViewModel.uiStateFlow.collectAsState()
        LaunchedEffect(state) {
            if (state is RegexScriptUiState.Finished) finish()
        }
        RegexScriptLayout(state) { mViewModel.emit(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(RegexScriptUiIntent.Init)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            RegexScriptViewEvent.OpenImporter -> {
                mImporter.launch(arrayOf("application/json", "text/*"))
            }
            is RegexScriptViewEvent.OpenExporter -> mExporter.launch(viewEvent.fileName)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}
