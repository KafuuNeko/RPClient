package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.ui.WorldBookListLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent
import me.kafuuneko.rpclient.libs.core.IViewEvent

/** 世界书列表页面宿主，桥接 JSON 导入导出文件选择器。 */
class WorldBookListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookListViewModel>()
    private var mPendingExportLorebookId: Long? = null
    private var mPendingArchiveLorebookIds: List<Long>? = null

    private val mImportWorldBookLauncher = registerForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult
        mViewModel.emit(WorldBookListUiIntent.ImportWorldBooks(uris))
    }

    private val mExportWorldBookLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val lorebookId = mPendingExportLorebookId ?: return@registerForActivityResult
        mPendingExportLorebookId = null
        uri ?: return@registerForActivityResult
        mViewModel.emit(WorldBookListUiIntent.ExportWorldBook(lorebookId, uri))
    }

    private val mExportArchiveLauncher = registerForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val ids = mPendingArchiveLorebookIds ?: return@registerForActivityResult
        mPendingArchiveLorebookIds = null
        uri ?: return@registerForActivityResult
        mViewModel.emit(WorldBookListUiIntent.ExportSelectedWorldBooks(ids, uri))
    }

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldBookListUiState.Finished) finish()
        }

        WorldBookListLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 文件选择器可能触发宿主重建，必须先恢复发起导出时冻结的目标。
        mPendingExportLorebookId =
            savedInstanceState?.takeIf { it.containsKey(KEY_EXPORT_ID) }?.getLong(KEY_EXPORT_ID)
        mPendingArchiveLorebookIds = savedInstanceState?.getLongArray(KEY_ARCHIVE_IDS)?.toList()
        mViewModel.emit(WorldBookListUiIntent.Init)
    }

    /** 保存系统选择器对应的目标，取消或收到结果后立即清空。 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mPendingExportLorebookId?.let { outState.putLong(KEY_EXPORT_ID, it) }
        mPendingArchiveLorebookIds?.let { outState.putLongArray(KEY_ARCHIVE_IDS, it.toLongArray()) }
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(WorldBookListUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        // 单文件与归档分别保存目标快照，系统选择器重建后仍能匹配原始选择。
        when (viewEvent) {
            WorldBookListViewEvent.OpenWorldBookImporter -> {
                mImportWorldBookLauncher.launch(
                    input = arrayOf(
                        "application/json",
                        "text/*",
                        "application/zip",
                        "application/x-zip-compressed"
                    )
                )
            }

            is WorldBookListViewEvent.OpenWorldBookExporter -> {
                mPendingExportLorebookId = viewEvent.lorebookId
                mExportWorldBookLauncher.launch(viewEvent.fileName)
            }

            is WorldBookListViewEvent.OpenWorldBookArchiveExporter -> {
                mPendingArchiveLorebookIds = viewEvent.lorebookIds
                mExportArchiveLauncher.launch(viewEvent.fileName)
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }

    private companion object {
        const val KEY_EXPORT_ID = "pendingExportLorebookId"
        const val KEY_ARCHIVE_IDS = "pendingArchiveLorebookIds"
    }
}
