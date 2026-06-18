package me.kafuuneko.rpclient.feature.requestlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogItem
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogDialogState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import me.kafuuneko.rpclient.libs.utils.formatTimestamp
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.json.JSONArray
import org.json.JSONObject

/** 请求日志页状态持有者，负责日志映射、复制、详情导航与批量清理。 */
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

    @UiIntentObserver(RequestLogUiIntent.ShowClearConfirmDialog::class)
    private fun onShowClearConfirmDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.ClearConfirm).setup()
    }

    @UiIntentObserver(RequestLogUiIntent.ConfirmClearLogs::class)
    private suspend fun onConfirmClearLogs() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.deleteAll()
        }
        uiState.copy(logs = emptyList(), dialogState = RequestLogDialogState.None).setup()
    }

    @UiIntentObserver(RequestLogUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.None).setup()
    }

    private suspend fun loadLogs(): List<RequestLogItem> {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getAllLogs().map { it.toUiModel() }
        }
    }

    private fun LLMRequestLog.toUiModel(): RequestLogItem {
        val mode = listOfNotNull(
            if (isStreaming) "stream" else "once",
            responseJson.cacheUsageSummary()
        ).joinToString(" 路 ")
        return RequestLogItem(
            id = id,
            title = "$providerName / $model",
            subtitle = "${createTime.formatTimestamp("MM-dd HH:mm:ss")} · ${protocol.name} · $mode",
            requestJson = requestJson,
            responseJson = responseJson
        )
    }

    private fun String.cacheUsageSummary(): String? {
        val usage = runCatching {
            val text = trim()
            when {
                text.startsWith("{") -> JSONObject(text).cacheUsage()
                text.startsWith("[") -> JSONArray(text).cacheUsage()
                else -> null
            }
        }.getOrNull() ?: return null
        if (usage.readTokens == null && usage.writeTokens == null) return null
        return listOfNotNull(
            usage.readTokens?.let { "cache read $it" },
            usage.writeTokens?.let { "cache write $it" }
        ).joinToString(" / ")
    }

    private fun JSONArray.cacheUsage(): CacheUsage? {
        var result: CacheUsage? = null
        for (index in 0 until length()) {
            val line = optString(index).trim()
            val data = if (line.startsWith("data:")) line.removePrefix("data:").trim() else line
            if (data.isBlank() || data == "[DONE]") continue
            val usage = runCatching { JSONObject(data).cacheUsage() }.getOrNull() ?: continue
            result = result.merge(usage)
        }
        return result
    }

    private fun JSONObject.cacheUsage(): CacheUsage? {
        val usage = optJSONObject("usage") ?: optJSONObject("usageMetadata") ?: this
        val promptDetails = usage.optJSONObject("prompt_tokens_details")
        val readTokens = promptDetails?.optNullableInt("cached_tokens")
            ?: usage.optFirstNullableInt(
                "cache_read_input_tokens",
                "cache_read_tokens",
                "input_cache_read",
                "prompt_cache_read_tokens",
                "cached_tokens",
                "cachedContentTokenCount"
            )
        val writeTokens = usage.optFirstNullableInt(
            "cache_creation_input_tokens",
            "cache_write_input_tokens",
            "cache_write_tokens",
            "input_cache_write",
            "prompt_cache_write_tokens"
        )
        return CacheUsage(readTokens, writeTokens).takeIf {
            it.readTokens != null || it.writeTokens != null
        }
    }

    private fun CacheUsage?.merge(other: CacheUsage): CacheUsage {
        if (this == null) return other
        return CacheUsage(
            readTokens = maxNullable(readTokens, other.readTokens),
            writeTokens = maxNullable(writeTokens, other.writeTokens)
        )
    }

    private fun maxNullable(left: Int?, right: Int?): Int? {
        return when {
            left == null -> right
            right == null -> left
            else -> maxOf(left, right)
        }
    }

    private fun JSONObject.optNullableInt(name: String): Int? {
        return if (has(name) && !isNull(name)) optInt(name) else null
    }

    private fun JSONObject.optFirstNullableInt(vararg names: String): Int? {
        for (name in names) {
            optNullableInt(name)?.let { return it }
        }
        return null
    }

    private data class CacheUsage(
        val readTokens: Int?,
        val writeTokens: Int?
    )
}
