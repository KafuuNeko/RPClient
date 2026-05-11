package me.kafuuneko.rpclient.feature.worldbooklist

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.feature.worldbookedit.WorldBookEditActivity
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorldBookListViewModel : CoreViewModelWithEvent<WorldBookListUiIntent, WorldBookListUiState>(
    WorldBookListUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()

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
}

