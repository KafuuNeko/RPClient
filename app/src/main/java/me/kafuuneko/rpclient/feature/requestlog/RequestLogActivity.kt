package me.kafuuneko.rpclient.feature.requestlog

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.jsonviewer.JsonViewerActivity
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.feature.requestlog.ui.RequestLogLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.libs.json.CreateJsonDocumentContract

/** LLM 请求日志页面宿主，处理系统保存选择器与 JSON 详情导航。 */
class RequestLogActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<RequestLogViewModel>()
    private var mPendingExportToken: String? = null
    private val mExportLauncher = registerForActivityResult(CreateJsonDocumentContract()) { uri ->
        val token = mPendingExportToken
        mPendingExportToken = null
        mViewModel.emit(RequestLogUiIntent.ExportResult(token, uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is RequestLogUiState.Finished) finish()
        }

        RequestLogLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mPendingExportToken = savedInstanceState?.getString(EXPORT_TOKEN)
        super.onCreate(savedInstanceState)
        mViewModel.emit(RequestLogUiIntent.Init)
    }

    /** 配置变更与系统回调使用同一个小型任务标识，不保存正文。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(EXPORT_TOKEN, mPendingExportToken)
        super.onSaveInstanceState(outState)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            is RequestLogViewEvent.OpenExporter -> {
                if (!mViewModel.isExportPending(viewEvent.export.token)) return
                mPendingExportToken = viewEvent.export.token
                try {
                    mExportLauncher.launch(viewEvent.export)
                } catch (_: Exception) {
                    mPendingExportToken = null
                    mViewModel.emit(RequestLogUiIntent.ExportResult(viewEvent.export.token, null))
                    Toast.makeText(this, R.string.json_export_failed, Toast.LENGTH_SHORT).show()
                }
            }

            is RequestLogViewEvent.OpenJson -> openJson(viewEvent.title, viewEvent.json)
            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private fun openJson(title: String, json: String) {
        startActivity(JsonViewerActivity.newIntent(this, title, json))
    }

    private companion object {
        const val EXPORT_TOKEN = "request_log_export_token"
    }
}
