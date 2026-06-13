package me.kafuuneko.rpclient.feature.worldbookentryedit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.feature.worldbookentryedit.ui.WorldBookEntryEditLayout
import me.kafuuneko.rpclient.libs.core.CoreActivityWithEvent

/** 世界书条目完整字段编辑页面宿主。 */
class WorldBookEntryEditActivity : CoreActivityWithEvent() {
    private val mViewModel by viewModels<WorldBookEntryEditViewModel>()

    override fun getViewEventFlow() = mViewModel.viewEventFlow

    @Composable
    override fun ViewContent() {
        val uiState by mViewModel.uiStateFlow.collectAsState()

        LaunchedEffect(uiState) {
            if (uiState is WorldBookEntryEditUiState.Finished) finish()
        }

        WorldBookEntryEditLayout(
            uiState = uiState,
            emit = { mViewModel.emit(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lorebookId = intent.getLongExtra(EXTRA_LOREBOOK_ID, 0L)
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, 0L).takeIf { it > 0L }
        mViewModel.emit(WorldBookEntryEditUiIntent.Init(lorebookId, entryId))
    }

    companion object {
        const val EXTRA_LOREBOOK_ID = "extra_lorebook_id"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
    }
}
