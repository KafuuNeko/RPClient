package me.kafuuneko.rpclient.feature.worldbookedit

import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.toComparableForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.WorldBookEntryEditActivity
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditDialogState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditLoadState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditMode
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorldBookEditViewModel : CoreViewModelWithEvent<WorldBookEditUiIntent, WorldBookEditUiState>(
    WorldBookEditUiState.None
), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()

    @UiIntentObserver(WorldBookEditUiIntent.Init::class)
    private suspend fun onInit(intent: WorldBookEditUiIntent.Init) {
        if (!isStateOf<WorldBookEditUiState.None>()) return
        WorldBookEditUiState.Normal(
            mode = if (intent.lorebookId == null) WorldBookEditMode.Create else WorldBookEditMode.Edit,
            form = WorldBookEditForm(),
            loadState = WorldBookEditLoadState.Loading
        ).setup()
        val form = intent.lorebookId?.let { lorebookId ->
            withContext(Dispatchers.IO) {
                val lorebook = mLorebookRepository.getLorebookById(lorebookId) ?: return@withContext null
                val entries = mLorebookRepository.getEntriesByLorebookId(lorebookId)
                WorldBookEditForm.from(lorebook, entries)
            }
        } ?: WorldBookEditForm()
        WorldBookEditUiState.Normal(
            mode = if (form.isNew) WorldBookEditMode.Create else WorldBookEditMode.Edit,
            form = form
        ).setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) return
        refreshEntries(uiState)
    }

    @UiIntentObserver(WorldBookEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookEditLoadState.None) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = WorldBookEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        WorldBookEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: WorldBookEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(WorldBookEditUiIntent.AddEntry::class)
    private suspend fun onAddEntry() {
        val lorebookId = saveWorldBookForEntryNavigation() ?: return
        AppViewEvent.StartActivity(
            activity = WorldBookEntryEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEntryEditActivity.EXTRA_LOREBOOK_ID, lorebookId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(WorldBookEditUiIntent.EditEntry::class)
    private suspend fun onEditEntry(intent: WorldBookEditUiIntent.EditEntry) {
        val lorebookId = saveWorldBookForEntryNavigation() ?: return
        AppViewEvent.StartActivity(
            activity = WorldBookEntryEditActivity::class.java,
            extras = Bundle().apply {
                putLong(WorldBookEntryEditActivity.EXTRA_LOREBOOK_ID, lorebookId)
                putLong(WorldBookEntryEditActivity.EXTRA_ENTRY_ID, intent.entryId)
            }
        ).tryEmit()
    }

    @UiIntentObserver(WorldBookEditUiIntent.SaveWorldBook::class)
    private suspend fun onSaveWorldBook() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        val form = uiState.form
        if (form.name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_name_empty).tryEmit()
            return
        }
        uiState.copy(loadState = WorldBookEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.saveLorebook(form.toLorebook())
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == WorldBookEditMode.Create) R.string.world_book_created else R.string.world_book_saved
        ).tryEmit()
        WorldBookEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.DeleteWorldBookClick::class)
    private fun onDeleteWorldBookClick() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            WorldBookEditUiState.Finished.setup()
            return
        }
        uiState.copy(
            dialogState = WorldBookEditDialogState.DeleteConfirm(
                worldBookName = uiState.form.name
            )
        ).setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.ConfirmDeleteWorldBook::class)
    private suspend fun onConfirmDeleteWorldBook() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = WorldBookEditLoadState.Deleting,
            dialogState = WorldBookEditDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.deleteLorebook(uiState.form.id)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_deleted).tryEmit()
        WorldBookEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        WorldBookEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(dialogState = WorldBookEditDialogState.None).setup()
    }

    private fun updateForm(block: WorldBookEditForm.() -> WorldBookEditForm) {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private suspend fun saveWorldBookForEntryNavigation(): Long? {
        val uiState = getOrNull<WorldBookEditUiState.Normal>() ?: return null
        if (uiState.form.name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_name_empty).tryEmit()
            return null
        }
        val lorebookId = withContext(Dispatchers.IO) {
            mLorebookRepository.saveLorebook(uiState.form.toLorebook())
        }
        val latestForm = uiState.form.copy(id = lorebookId)
        uiState.copy(
            mode = WorldBookEditMode.Edit,
            form = latestForm,
            initialForm = latestForm.toComparableForm()
        ).setup()
        return lorebookId
    }

    private suspend fun refreshEntries(uiState: WorldBookEditUiState.Normal) {
        val form = withContext(Dispatchers.IO) {
            val lorebook = mLorebookRepository.getLorebookById(uiState.form.id) ?: return@withContext null
            val entries = mLorebookRepository.getEntriesByLorebookId(uiState.form.id)
            WorldBookEditForm.from(lorebook, entries)
        } ?: return
        getOrNull<WorldBookEditUiState.Normal>()?.copy(form = form, initialForm = form)?.setup()
    }

    private fun WorldBookEditForm.hasUnsavedChangesFrom(initialForm: WorldBookEditForm): Boolean {
        return toComparableForm() != initialForm.toComparableForm()
    }

}
