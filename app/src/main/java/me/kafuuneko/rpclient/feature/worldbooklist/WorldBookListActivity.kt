package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.ui.WorldBookListLayout
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import me.kafuuneko.rpclient.libs.core.IViewEvent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListViewEvent
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

class WorldBookListActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookListViewModel>()
    private var mPendingExportLorebookId: Long? = null

    private val mImportWorldBookLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mViewModel.emit(WorldBookListUiIntent.ImportWorldBook(uri))
    }

    private val mExportWorldBookLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val lorebookId = mPendingExportLorebookId ?: return@registerForActivityResult
        mPendingExportLorebookId = null
        uri ?: return@registerForActivityResult
        mViewModel.emit(WorldBookListUiIntent.ExportWorldBook(lorebookId, uri))
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
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.emit(WorldBookListUiIntent.Init)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.emit(WorldBookListUiIntent.Resume)
    }

    override suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        when (viewEvent) {
            WorldBookListViewEvent.OpenWorldBookImporter -> {
                mImportWorldBookLauncher.launch(
                    arrayOf("application/json", "text/*")
                )
            }

            is WorldBookListViewEvent.OpenWorldBookExporter -> {
                mPendingExportLorebookId = viewEvent.lorebookId
                mExportWorldBookLauncher.launch(viewEvent.fileName)
            }

            else -> super.onReceivedViewEvent(viewEvent)
        }
    }
}

