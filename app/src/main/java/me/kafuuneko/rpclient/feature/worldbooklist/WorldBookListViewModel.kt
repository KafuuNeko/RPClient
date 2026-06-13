package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.worldbookedit.WorldBookEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import me.kafuuneko.rpclient.R
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 世界书列表页状态持有者，协调条目数聚合、编辑导航及文件导入导出。 */
class WorldBookListViewModel : CoreViewModelWithEvent<WorldBookListUiIntent, WorldBookListUiState>(
    WorldBookListUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()
    private val mContext by inject<Context>()

    @UiIntentObserver(WorldBookListUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<WorldBookListUiState.None>()) return
        WorldBookListUiState.Normal(loadState = WorldBookListLoadState.Loading).setup()
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.Resume::class)
    private suspend fun onResume() {
        if (!isStateOf<WorldBookListUiState.Normal>()) return
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.Back::class)
    private fun onBack() {
        WorldBookListUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookListUiIntent.CreateWorldBook::class)
    private fun onCreateWorldBook() {
        AppViewEvent.StartActivity(WorldBookEditActivity::class.java).tryEmit()
    }

    @UiIntentObserver(WorldBookListUiIntent.EditWorldBook::class)
    private fun onEditWorldBook(intent: WorldBookListUiIntent.EditWorldBook) {
        AppViewEvent.StartActivity(
            activity = WorldBookEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEditActivity.EXTRA_LOREBOOK_ID, intent.lorebookId)
            }
        ).tryEmit()
    }

    private suspend fun refreshLorebooks() {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        val items = withContext(Dispatchers.IO) {
            mLorebookRepository.getAllLorebooks().map { lorebook ->
                WorldBookListItem.from(
                    lorebook = lorebook,
                    entryCount = mLorebookRepository.getEntriesByLorebookId(lorebook.id).size
                )
            }
        }
        uiState.copy(
            loadState = WorldBookListLoadState.None,
            lorebooks = items
        ).setup()
    }

    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBookClick::class)
    private fun onImportWorldBookClick() {
        WorldBookListViewEvent.OpenWorldBookImporter.tryEmit()
    }

    @UiIntentObserver(WorldBookListUiIntent.ImportWorldBook::class)
    private suspend fun onImportWorldBook(intent: WorldBookListUiIntent.ImportWorldBook) {
        val uiState = getOrNull<WorldBookListUiState.Normal>() ?: return
        uiState.copy(loadState = WorldBookListLoadState.Loading).setup()
        
        runCatching {
            withContext(Dispatchers.IO) { mLorebookRepository.importFromUri(intent.uri) }
        }.getOrElse {
            AppViewEvent.PopupToastMessage(it.message ?: mContext.getString(R.string.import_world_book_failed)).tryEmit()
            refreshLorebooks()
            return
        }
        
        AppViewEvent.PopupToastMessageByResId(R.string.import_world_book_success).tryEmit()
        refreshLorebooks()
    }

    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBookClick::class)
    private suspend fun onExportWorldBookClick(intent: WorldBookListUiIntent.ExportWorldBookClick) {
        val lorebook = withContext(Dispatchers.IO) {
            mLorebookRepository.getLorebookById(intent.lorebookId)
        } ?: return
        
        WorldBookListViewEvent.OpenWorldBookExporter(
            lorebookId = intent.lorebookId,
            fileName = "${lorebook.name.ifBlank { "worldbook" }}.json"
        ).tryEmit()
    }

    @UiIntentObserver(WorldBookListUiIntent.ExportWorldBook::class)
    private suspend fun onExportWorldBook(intent: WorldBookListUiIntent.ExportWorldBook) {
        val json = runCatching {
            withContext(Dispatchers.IO) { mLorebookRepository.exportJson(intent.lorebookId) }
        }.getOrElse {
            AppViewEvent.PopupToastMessage(it.message ?: mContext.getString(R.string.export_world_book_failed)).tryEmit()
            return
        }
        
        runCatching {
            withContext(Dispatchers.IO) {
                mContext.contentResolver.openOutputStream(intent.uri)?.use { output ->
                    output.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open export destination")
            }
        }.onSuccess {
            AppViewEvent.PopupToastMessageByResId(R.string.export_world_book_success).tryEmit()
        }.onFailure {
            AppViewEvent.PopupToastMessage(it.message ?: mContext.getString(R.string.export_world_book_failed)).tryEmit()
        }
    }
}
