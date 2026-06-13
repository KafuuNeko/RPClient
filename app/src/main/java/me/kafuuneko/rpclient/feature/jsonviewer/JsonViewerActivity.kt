package me.kafuuneko.rpclient.feature.jsonviewer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiIntent
import me.kafuuneko.rpclient.feature.jsonviewer.presentation.JsonViewerUiState
import me.kafuuneko.rpclient.feature.jsonviewer.ui.JsonViewerLayout
import me.kafuuneko.rpclient.libs.core.CoreActivity

/** JSON 分层查看页面宿主，负责临时载荷 key 的生命周期清理。 */
class JsonViewerActivity : CoreActivity() {
    private val mViewModel by viewModels<JsonViewerViewModel>()
    private val mPayloadKey by lazy { intent.getStringExtra(EXTRA_PAYLOAD_KEY) }

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is JsonViewerUiState.Finished) finish()
        }

        JsonViewerLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(JsonViewerUiIntent.Init(mPayloadKey))
    }

    override fun onDestroy() {
        if (isFinishing) JsonViewerPayloadStore.remove(mPayloadKey)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PAYLOAD_KEY = "json_viewer_payload_key"

        fun newIntent(context: Context, title: String, json: String): Intent {
            val payloadKey = JsonViewerPayloadStore.put(title = title, json = json)
            return Intent(context, JsonViewerActivity::class.java)
                .putExtra(EXTRA_PAYLOAD_KEY, payloadKey)
        }
    }
}
