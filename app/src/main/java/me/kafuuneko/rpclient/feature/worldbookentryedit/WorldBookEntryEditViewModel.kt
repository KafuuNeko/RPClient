package me.kafuuneko.rpclient.feature.worldbookentryedit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.toComparableForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditDialogState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditLoadState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditMode
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.repository.LorebookRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WorldBookEntryEditViewModel :
    CoreViewModelWithEvent<WorldBookEntryEditUiIntent, WorldBookEntryEditUiState>(
        WorldBookEntryEditUiState.None
    ), KoinComponent {
    private val mLorebookRepository by inject<LorebookRepository>()

    @UiIntentObserver(WorldBookEntryEditUiIntent.Init::class)
    private suspend fun onInit(intent: WorldBookEntryEditUiIntent.Init) {
        if (!isStateOf<WorldBookEntryEditUiState.None>()) return
        WorldBookEntryEditUiState.Normal(
            mode = if (intent.entryId == null) WorldBookEntryEditMode.Create else WorldBookEntryEditMode.Edit,
            form = WorldBookEntryEditForm(lorebookId = intent.lorebookId),
            loadState = WorldBookEntryEditLoadState.Loading
        ).setup()
        val form = intent.entryId?.let { entryId ->
            withContext(Dispatchers.IO) {
                mLorebookRepository.getEntryById(entryId)?.let { WorldBookEntryEditForm.from(it) }
            }
        } ?: WorldBookEntryEditForm(lorebookId = intent.lorebookId)
        WorldBookEntryEditUiState.Normal(
            mode = if (form.isNew) WorldBookEntryEditMode.Create else WorldBookEntryEditMode.Edit,
            form = form
        ).setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.Back::class)
    private fun onBack() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.loadState != WorldBookEntryEditLoadState.None) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = WorldBookEntryEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        WorldBookEntryEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: WorldBookEntryEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.AddKeyword::class)
    private fun onAddKeyword() =
        updateForm { copy(keywords = keywords + "") }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeKeyword::class)
    private fun onChangeKeyword(intent: WorldBookEntryEditUiIntent.ChangeKeyword) =
        updateForm { copy(keywords = keywords.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.DeleteKeyword::class)
    private fun onDeleteKeyword(intent: WorldBookEntryEditUiIntent.DeleteKeyword) =
        updateForm { copy(keywords = keywords.removeAtOrSelf(intent.index).ifEmpty { listOf("") }) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.AddSecondaryKeyword::class)
    private fun onAddSecondaryKeyword() =
        updateForm { copy(secondaryKeywords = secondaryKeywords + "") }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeSecondaryKeyword::class)
    private fun onChangeSecondaryKeyword(intent: WorldBookEntryEditUiIntent.ChangeSecondaryKeyword) =
        updateForm { copy(secondaryKeywords = secondaryKeywords.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.DeleteSecondaryKeyword::class)
    private fun onDeleteSecondaryKeyword(intent: WorldBookEntryEditUiIntent.DeleteSecondaryKeyword) =
        updateForm { copy(secondaryKeywords = secondaryKeywords.removeAtOrSelf(intent.index).ifEmpty { listOf("") }) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeConstant::class)
    private fun onChangeConstant(intent: WorldBookEntryEditUiIntent.ChangeConstant) =
        updateForm { copy(constant = intent.value) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.AddCategory::class)
    private fun onAddCategory() =
        updateForm { copy(category = category + "") }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeCategory::class)
    private fun onChangeCategory(intent: WorldBookEntryEditUiIntent.ChangeCategory) =
        updateForm { copy(category = category.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.DeleteCategory::class)
    private fun onDeleteCategory(intent: WorldBookEntryEditUiIntent.DeleteCategory) =
        updateForm { copy(category = category.removeAtOrSelf(intent.index).ifEmpty { listOf("") }) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeOrder::class)
    private fun onChangeOrder(intent: WorldBookEntryEditUiIntent.ChangeOrder) =
        updateForm { copy(order = intent.value) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeDepth::class)
    private fun onChangeDepth(intent: WorldBookEntryEditUiIntent.ChangeDepth) =
        updateForm { copy(depth = intent.value) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ChangeContent::class)
    private fun onChangeContent(intent: WorldBookEntryEditUiIntent.ChangeContent) =
        updateForm { copy(content = intent.value) }

    @UiIntentObserver(WorldBookEntryEditUiIntent.SaveEntry::class)
    private suspend fun onSaveEntry() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.form.order.trim().toIntOrNull() == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_order_invalid).tryEmit()
            return
        }
        if (uiState.form.depth.trim().toIntOrNull() == null) {
            AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_depth_invalid).tryEmit()
            return
        }
        val entry = uiState.form.toLorebookEntryOrNull() ?: return
        uiState.copy(loadState = WorldBookEntryEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.saveEntry(entry)
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == WorldBookEntryEditMode.Create) R.string.world_book_entry_created else R.string.world_book_entry_saved
        ).tryEmit()
        WorldBookEntryEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.DeleteEntryClick::class)
    private fun onDeleteEntryClick() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            WorldBookEntryEditUiState.Finished.setup()
            return
        }
        uiState.copy(
            dialogState = WorldBookEntryEditDialogState.DeleteConfirm(
                entryName = uiState.form.name
            )
        ).setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ConfirmDeleteEntry::class)
    private suspend fun onConfirmDeleteEntry() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = WorldBookEntryEditLoadState.Deleting,
            dialogState = WorldBookEntryEditDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            mLorebookRepository.deleteEntry(uiState.form.id)
        }
        AppViewEvent.PopupToastMessageByResId(R.string.world_book_entry_deleted).tryEmit()
        WorldBookEntryEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.ConfirmDiscardChanges::class)
    private fun onConfirmDiscardChanges() {
        WorldBookEntryEditUiState.Finished.setup()
    }

    @UiIntentObserver(WorldBookEntryEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        uiState.copy(dialogState = WorldBookEntryEditDialogState.None).setup()
    }

    private fun updateForm(block: WorldBookEntryEditForm.() -> WorldBookEntryEditForm) {
        val uiState = getOrNull<WorldBookEntryEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private fun WorldBookEntryEditForm.hasUnsavedChangesFrom(initialForm: WorldBookEntryEditForm): Boolean {
        return toComparableForm() != initialForm.toComparableForm()
    }

    private fun List<String>.updateAt(index: Int, value: String): List<String> {
        if (index !in indices) return this
        return mapIndexed { currentIndex, item ->
            if (currentIndex == index) value else item
        }
    }

    private fun <T> List<T>.removeAtOrSelf(index: Int): List<T> {
        if (index !in indices) return this
        return filterIndexed { currentIndex, _ -> currentIndex != index }
    }
}
