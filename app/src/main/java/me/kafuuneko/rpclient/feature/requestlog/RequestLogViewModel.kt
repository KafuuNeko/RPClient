package me.kafuuneko.rpclient.feature.requestlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestLogViewModel : CoreViewModelWithEvent<RequestLogUiIntent, RequestLogUiState>(
    RequestLogUiState.None
), KoinComponent {
    private val mLLMRequestLogRepository by inject<LLMRequestLogRepository>()

    @UiIntentObserver(RequestLogUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<RequestLogUiState.None>()) return
        RequestLogUiState.Normal(logs = loadLogs()).setup()
    }

    @UiIntentObserver(RequestLogUiIntent.Back::class)
    private fun onBack() {
        RequestLogUiState.Finished.setup()
    }

    @UiIntentObserver(RequestLogUiIntent.CopyRequestJson::class)
    private suspend fun onCopyRequestJson(intent: RequestLogUiIntent.CopyRequestJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.CopyText(log.requestJson).emit()
    }

    @UiIntentObserver(RequestLogUiIntent.CopyResponseJson::class)
    private suspend fun onCopyResponseJson(intent: RequestLogUiIntent.CopyResponseJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.CopyText(log.responseJson).emit()
    }

    @UiIntentObserver(RequestLogUiIntent.OpenRequestJson::class)
    private suspend fun onOpenRequestJson(intent: RequestLogUiIntent.OpenRequestJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = log.requestJson).emit()
    }

    @UiIntentObserver(RequestLogUiIntent.OpenResponseJson::class)
    private suspend fun onOpenResponseJson(intent: RequestLogUiIntent.OpenResponseJson) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == intent.logId } ?: return
        RequestLogViewEvent.OpenJson(title = "${log.title} / ${intent.title}", json = log.responseJson).emit()
    }

    private suspend fun loadLogs(): List<RequestLogItem> {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getAllLogs().map { it.toUiModel() }
        }
    }

    private fun LLMRequestLog.toUiModel(): RequestLogItem {
        val mode = if (isStreaming) "stream" else "once"
        return RequestLogItem(
            id = id,
            title = "$providerName / $model",
            subtitle = "${createTime.toDisplayTime()} · ${protocol.name} · $mode",
            requestJson = requestJson,
            responseJson = responseJson
        )
    }

    private fun Long.toDisplayTime(): String {
        return SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
    }
}
