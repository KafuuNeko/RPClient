package me.kafuuneko.rpclient.feature.requestlog

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.requestlog.model.RequestLogPage
import me.kafuuneko.rpclient.feature.requestlog.model.toUiModel
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogDialogState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogExportState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiIntent
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogUiState
import me.kafuuneko.rpclient.feature.requestlog.presentation.RequestLogViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.json.JsonExportRepository
import me.kafuuneko.rpclient.libs.json.PreparedJsonExport
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.room.repository.LLMRequestLogRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 请求日志页状态持有者。
 *
 * 核心职责：
 * - 从数据库分批拉取请求日志元数据和有限预览；
 * - 冻结并导出格式化请求体与响应体，不受剪贴板长度限制；
 * - 驱动 JSON 查看器（JsonViewer）全屏展开浏览；
 * - 支持清空全部历史请求日志并更新 UI 展示。
 */
class RequestLogViewModel : CoreViewModelWithEvent<RequestLogUiIntent, RequestLogUiState>(
    RequestLogUiState.None
), KoinComponent {
    private val mLLMRequestLogRepository by inject<LLMRequestLogRepository>()
    private val mJsonExportRepository by inject<JsonExportRepository>()
    private var mExportJob: Job? = null
    private var mPreparedExport: PreparedJsonExport? = null
    private var mExportGeneration = 0L

    /** 系统选择器启动前确认事件仍属于当前任务，取消后的排队事件不再打开窗口。 */
    fun isExportPending(token: String): Boolean =
        mPreparedExport?.token == token &&
            getOrNull<RequestLogUiState.Normal>()?.exportState == RequestLogExportState.Selecting

    /** 初始化日志列表，从数据库加载首批轻量摘要。 */
    @UiIntentObserver(RequestLogUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<RequestLogUiState.None>()) return
        val page = loadLogPage(offset = 0)
        RequestLogUiState.Normal(
            logs = page.items,
            canLoadMore = page.canLoadMore,
        ).setup()
    }

    /** 滚动至列表末尾时继续加载下一批轻量日志摘要。 */
    @UiIntentObserver(RequestLogUiIntent.LoadMore::class)
    private suspend fun onLoadMore() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (!uiState.canLoadMore || uiState.isLoadingMore) return

        uiState.copy(isLoadingMore = true).setup()
        val page = loadLogPage(offset = uiState.logs.size)
        val latest = getOrNull<RequestLogUiState.Normal>() ?: return
        latest.copy(
            logs = uiState.logs + page.items,
            canLoadMore = page.canLoadMore,
            isLoadingMore = false,
        ).setup()
    }

    /** 处理返回操作，迁移至 Finished 状态。 */
    @UiIntentObserver(RequestLogUiIntent.Back::class)
    private fun onBack() {
        if (isStateOf<RequestLogUiState.Finished>()) return
        cancelExport()
        RequestLogUiState.finished(uiStateFlow.value).setup()
    }

    /** 准备指定请求的冻结导出快照。 */
    @UiIntentObserver(RequestLogUiIntent.ExportRequestJson::class)
    private fun onExportRequestJson(intent: RequestLogUiIntent.ExportRequestJson) =
        startExport(intent.logId, request = true)

    /** 准备指定响应的冻结导出快照。 */
    @UiIntentObserver(RequestLogUiIntent.ExportResponseJson::class)
    private fun onExportResponseJson(intent: RequestLogUiIntent.ExportResponseJson) =
        startExport(intent.logId, request = false)

    /** 冻结内容后才启动系统选择器，避免清空日志或连续点击造成串写。 */
    private fun startExport(logId: Long, request: Boolean) {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        if (uiState.exportState != RequestLogExportState.Idle) return
        if (uiState.logs.none { it.id == logId }) return
        val generation = ++mExportGeneration
        uiState.copy(exportState = RequestLogExportState.Preparing).setup()
        mExportJob = viewModelScope.launch {
            try {
                // 准备工作使用独立协程，不阻塞返回和取消 Intent。
                val json = if (request) loadRequestJson(logId) else loadResponseJson(logId)
                requireNotNull(json) { "Request log is unavailable." }
                val suffix = if (request) "request" else "response"
                val prepared = mJsonExportRepository.prepare(json, "request-log-$logId-$suffix")
                mPreparedExport = prepared
                updateExportState(RequestLogExportState.Selecting)
                if (prepared.mimeType != "application/json") {
                    AppViewEvent.PopupToastMessageByResId(R.string.json_export_raw_notice).emit()
                }
                RequestLogViewEvent.OpenExporter(prepared).emit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == mExportGeneration) {
                    mPreparedExport?.let { mJsonExportRepository.discard(it.token) }
                    mPreparedExport = null
                    updateExportState(RequestLogExportState.Idle)
                    AppViewEvent.PopupToastMessageByResId(R.string.json_export_failed).emit()
                }
            }
        }
    }

    /** 校验选择器结果归属，取消时释放快照，确认后异步写入。 */
    @UiIntentObserver(RequestLogUiIntent.ExportResult::class)
    private fun onExportResult(intent: RequestLogUiIntent.ExportResult) {
        val prepared = mPreparedExport
        if (prepared == null || prepared.token != intent.token) {
            if (intent.uri != null) {
                AppViewEvent.PopupToastMessageByResId(R.string.json_export_failed).tryEmit()
            }
            return
        }
        if (intent.uri == null) {
            cancelExport()
            updateExportState(RequestLogExportState.Idle)
            return
        }
        // 仅同一份冻结文件能够进入写入阶段，系统回调不重新读取日志。
        mPreparedExport = null
        val generation = ++mExportGeneration
        updateExportState(RequestLogExportState.Writing)
        mExportJob = viewModelScope.launch {
            try {
                mJsonExportRepository.write(prepared.token, intent.uri)
                AppViewEvent.PopupToastMessageByResId(R.string.json_export_success).emit()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation == mExportGeneration) {
                    AppViewEvent.PopupToastMessageByResId(R.string.json_export_failed).emit()
                }
            } finally {
                if (generation == mExportGeneration) updateExportState(RequestLogExportState.Idle)
            }
        }
    }

    /** 用户主动取消准备或写入，等待选择器时由系统自身处理取消。 */
    @UiIntentObserver(RequestLogUiIntent.CancelExport::class)
    private fun onCancelExport() {
        cancelExport()
        updateExportState(RequestLogExportState.Idle)
    }

    /** 保留当前日志列表和对话框，只更新导出子状态。 */
    private fun updateExportState(state: RequestLogExportState) {
        getOrNull<RequestLogUiState.Normal>()?.copy(exportState = state)?.setup()
    }

    /** 关闭私有导出任务及尚未交付的文件。 */
    private fun cancelExport() {
        mExportGeneration++
        mExportJob?.cancel()
        mPreparedExport?.let { mJsonExportRepository.discard(it.token) }
        mPreparedExport = null
    }

    /** 页面彻底释放时清理导出快照；配置变更保留 ViewModel 中的任务。 */
    override fun onCleared() {
        cancelExport()
        super.onCleared()
    }

    /** 打开 JSON 查看器展示指定日志的完整请求体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenRequestJson::class)
    private suspend fun onOpenRequestJson(intent: RequestLogUiIntent.OpenRequestJson) {
        openJson(intent.logId, intent.title, request = true)
    }

    /** 打开 JSON 查看器展示指定日志的完整响应体。 */
    @UiIntentObserver(RequestLogUiIntent.OpenResponseJson::class)
    private suspend fun onOpenResponseJson(intent: RequestLogUiIntent.OpenResponseJson) {
        openJson(intent.logId, intent.title, request = false)
    }

    /** 受控读取正文，损坏或超预算的历史记录不会使 Intent 收集协程崩溃。 */
    private suspend fun openJson(logId: Long, title: String, request: Boolean) {
        val log = getOrNull<RequestLogUiState.Normal>()?.logs?.firstOrNull { it.id == logId }
            ?: return
        try {
            // 正文仍按需读取；列表和其余日志不参与本次查看。
            val json = if (request) loadRequestJson(logId) else loadResponseJson(logId)
            requireNotNull(json) { "Request log is unavailable." }
            RequestLogViewEvent.OpenJson(title = "${log.title} / $title", json = json).emit()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            AppViewEvent.PopupToastMessageByResId(R.string.json_payload_unavailable).emit()
        }
    }

    /** 弹出清空全部日志的二次确认弹窗。 */
    @UiIntentObserver(RequestLogUiIntent.ShowClearConfirmDialog::class)
    private fun onShowClearConfirmDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.ClearConfirm).setup()
    }

    /** 用户确认清空日志，删除数据库全部记录并清空列表。 */
    @UiIntentObserver(RequestLogUiIntent.ConfirmClearLogs::class)
    private suspend fun onConfirmClearLogs() {
        getOrNull<RequestLogUiState.Normal>() ?: return
        withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.deleteAll()
        }
        getOrNull<RequestLogUiState.Normal>()?.copy(
            logs = emptyList(),
            canLoadMore = false,
            dialogState = RequestLogDialogState.None,
        )?.setup()
    }

    /** 关闭当前弹窗。 */
    @UiIntentObserver(RequestLogUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<RequestLogUiState.Normal>() ?: return
        uiState.copy(dialogState = RequestLogDialogState.None).setup()
    }

    /** 在 IO 线程读取一批轻量日志摘要，多取一条用于判断是否还有下一页。 */
    private suspend fun loadLogPage(offset: Int): RequestLogPage {
        return withContext(Dispatchers.IO) {
            val overviews = mLLMRequestLogRepository.getLogOverviews(
                previewLength = JSON_PREVIEW_LENGTH,
                limit = PAGE_SIZE + 1,
                offset = offset,
            )
            RequestLogPage(
                items = overviews.take(PAGE_SIZE)
                    .map { llmRequestLogOverview -> llmRequestLogOverview.toUiModel() },
                canLoadMore = overviews.size > PAGE_SIZE,
            )
        }
    }

    /** 从数据库异步加载指定日志的完整请求体 JSON。 */
    private suspend fun loadRequestJson(logId: Long): String? {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getRequestJson(logId)
        }
    }

    /** 从数据库异步加载指定日志的完整响应体 JSON。 */
    private suspend fun loadResponseJson(logId: Long): String? {
        return withContext(Dispatchers.IO) {
            mLLMRequestLogRepository.getResponseJson(logId)
        }
    }

    private companion object {
        /** 单页日志数量。 */
        const val PAGE_SIZE = 50

        /** 列表展示时的 JSON 预览文本截断最大长度。 */
        const val JSON_PREVIEW_LENGTH = 2_000
    }

}
