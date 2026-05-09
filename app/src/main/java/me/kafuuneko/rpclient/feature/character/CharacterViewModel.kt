package me.kafuuneko.rpclient.feature.character

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.character.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.character.presentation.CharacterDialogState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterEditorState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterLoadState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterViewModel : CoreViewModelWithEvent<CharacterUiIntent, CharacterUiState>(
    CharacterUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()
    private val mGson by inject<Gson>()

    @UiIntentObserver(CharacterUiIntent.Init::class)
    private suspend fun onInit() {
        if (!isStateOf<CharacterUiState.None>()) return
        CharacterUiState.Normal(loadState = CharacterLoadState.Loading).setup()
        refreshCharacters(selectedCharacterId = null, keepEditor = false)
    }

    @UiIntentObserver(CharacterUiIntent.Resume::class)
    private suspend fun onResume() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        if (uiState.loadState != CharacterLoadState.None) return
        refreshCharacters(
            selectedCharacterId = uiState.selectedCharacterId,
            keepEditor = uiState.editorState is CharacterEditorState.Editing
        )
    }

    @UiIntentObserver(CharacterUiIntent.Back::class)
    private suspend fun onBack() {
        cleanupPendingAvatar()
        CharacterUiState.Finished.setup()
    }

    @UiIntentObserver(CharacterUiIntent.ChangeSearchText::class)
    private fun onChangeSearchText(intent: CharacterUiIntent.ChangeSearchText) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        uiState.copy(searchText = intent.value).setup()
    }

    @UiIntentObserver(CharacterUiIntent.SelectCharacter::class)
    private suspend fun onSelectCharacter(intent: CharacterUiIntent.SelectCharacter) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val character = uiState.characters.firstOrNull { it.id == intent.characterId } ?: return
        cleanupPendingAvatar()
        uiState.copy(
            selectedCharacterId = character.id,
            editorState = CharacterEditorState.Editing(CharacterEditForm.from(character)),
            dialogState = CharacterDialogState.None
        ).setup()
    }

    @UiIntentObserver(CharacterUiIntent.CreateCharacter::class)
    private suspend fun onCreateCharacter() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        cleanupPendingAvatar()
        uiState.copy(
            selectedCharacterId = null,
            editorState = CharacterEditorState.Editing(CharacterEditForm()),
            dialogState = CharacterDialogState.None
        ).setup()
    }

    @UiIntentObserver(CharacterUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(CharacterUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        if (uiState.editorState !is CharacterEditorState.Editing) return
        CharacterViewEvent.OpenAvatarPicker.tryEmit()
    }

    @UiIntentObserver(CharacterUiIntent.AvatarSelected::class)
    private suspend fun onAvatarSelected(intent: CharacterUiIntent.AvatarSelected) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val editorState = uiState.editorState as? CharacterEditorState.Editing ?: return
        uiState.copy(loadState = CharacterLoadState.Saving).setup()
        val avatarUuid = runCatching {
            withContext(Dispatchers.IO) {
                val uuid = mFileRepository.saveFile(intent.uri)
                if (
                    editorState.form.avatar.isNotBlank() &&
                    editorState.form.avatar != editorState.form.originalAvatar &&
                    editorState.form.avatar != uuid
                ) {
                    mFileRepository.deleteFile(editorState.form.avatar)
                }
                uuid
            }
        }.getOrElse {
            val latestState = getOrNull<CharacterUiState.Normal>() ?: return
            latestState.copy(loadState = CharacterLoadState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
            return
        }
        val avatarPath = withContext(Dispatchers.IO) {
            mFileRepository.getFile(avatarUuid)?.absolutePath
        }
        val latestState = getOrNull<CharacterUiState.Normal>() ?: return
        latestState.copy(
            loadState = CharacterLoadState.None,
            avatarFilePaths = latestState.avatarFilePaths + listOfNotNull(
                avatarPath?.let { avatarUuid to it }
            ),
            editorState = CharacterEditorState.Editing(editorState.form.copy(avatar = avatarUuid))
        ).setup()
    }

    @UiIntentObserver(CharacterUiIntent.ChangeTagsText::class)
    private fun onChangeTagsText(intent: CharacterUiIntent.ChangeTagsText) =
        updateForm { copy(tagsText = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangeFirstMessages::class)
    private fun onChangeFirstMessages(intent: CharacterUiIntent.ChangeFirstMessages) =
        updateForm { copy(firstMessages = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    @UiIntentObserver(CharacterUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    @UiIntentObserver(CharacterUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val editorState = uiState.editorState as? CharacterEditorState.Editing ?: return
        val character = editorState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterLoadState.Saving).setup()
        val savedId = withContext(Dispatchers.IO) {
            val savedId = mCharacterRepository.saveCharacter(character)
            if (
                editorState.form.originalAvatar.isNotBlank() &&
                editorState.form.originalAvatar != character.avatar
            ) {
                mFileRepository.deleteFile(editorState.form.originalAvatar)
            }
            savedId
        }
        AppViewEvent.PopupToastMessageByResId(
            if (character.id == 0L) R.string.character_created else R.string.character_saved
        ).tryEmit()
        refreshCharacters(selectedCharacterId = savedId, keepEditor = false)
    }

    @UiIntentObserver(CharacterUiIntent.DeleteCharacterClick::class)
    private suspend fun onDeleteCharacterClick() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val editorState = uiState.editorState as? CharacterEditorState.Editing ?: return
        if (editorState.form.isNew) {
            editorState.form.cleanupPendingAvatar()
            uiState.copy(editorState = CharacterEditorState.None).setup()
            return
        }
        uiState.copy(
            dialogState = CharacterDialogState.DeleteConfirm(
                characterId = editorState.form.id,
                characterName = editorState.form.name
            )
        ).setup()
    }

    @UiIntentObserver(CharacterUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val dialogState = uiState.dialogState as? CharacterDialogState.DeleteConfirm ?: return
        val pendingAvatar = (uiState.editorState as? CharacterEditorState.Editing)
            ?.form
            ?.takeIf { it.avatar.isNotBlank() && it.avatar != it.originalAvatar }
            ?.avatar
        uiState.copy(
            loadState = CharacterLoadState.Deleting,
            dialogState = CharacterDialogState.None
        ).setup()
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(dialogState.characterId)
            mCharacterRepository.deleteCharacter(dialogState.characterId)
            character?.avatar?.takeIf { it.isNotBlank() }?.let {
                mFileRepository.deleteFile(it)
            }
            pendingAvatar?.let {
                mFileRepository.deleteFile(it)
            }
        }
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        refreshCharacters(selectedCharacterId = null, keepEditor = false)
    }

    @UiIntentObserver(CharacterUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        uiState.copy(dialogState = CharacterDialogState.None).setup()
    }

    /**
     * 统一更新角色表单字段，保证 UI 表单状态只从 ViewModel 推送。
     */
    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val editorState = uiState.editorState as? CharacterEditorState.Editing ?: return
        uiState.copy(
            editorState = CharacterEditorState.Editing(editorState.form.block())
        ).setup()
    }

    private suspend fun refreshCharacters(
        selectedCharacterId: Long?,
        keepEditor: Boolean
    ) {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val characters = withContext(Dispatchers.IO) {
            mCharacterRepository.getAllCharacters()
        }
        val currentEditor = uiState.editorState
        val avatarFilePaths = withContext(Dispatchers.IO) {
            val characterAvatarPaths = characters.mapNotNull { character ->
                character.avatar
                    .takeIf { it.isNotBlank() }
                    ?.let { uuid -> mFileRepository.getFile(uuid)?.absolutePath?.let { uuid to it } }
            }
            val editorAvatarPath = (currentEditor as? CharacterEditorState.Editing)
                ?.form
                ?.avatar
                ?.takeIf { it.isNotBlank() }
                ?.let { uuid -> mFileRepository.getFile(uuid)?.absolutePath?.let { uuid to it } }
            (characterAvatarPaths + listOfNotNull(editorAvatarPath)).toMap()
        }
        val selected = when {
            keepEditor && currentEditor is CharacterEditorState.Editing && currentEditor.form.isNew -> null
            keepEditor && currentEditor is CharacterEditorState.Editing -> characters.firstOrNull { it.id == currentEditor.form.id }
            else -> characters.firstOrNull { it.id == selectedCharacterId } ?: characters.firstOrNull()
        }
        val editorState = when {
            keepEditor && currentEditor is CharacterEditorState.Editing -> currentEditor
            selected != null -> CharacterEditorState.Editing(CharacterEditForm.from(selected))
            else -> CharacterEditorState.None
        }
        uiState.copy(
            loadState = CharacterLoadState.None,
            characters = characters,
            avatarFilePaths = avatarFilePaths,
            selectedCharacterId = selected?.id,
            editorState = editorState
        ).setup()
    }

    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter(mGson)
    }

    private suspend fun cleanupPendingAvatar() {
        val uiState = getOrNull<CharacterUiState.Normal>() ?: return
        val editorState = uiState.editorState as? CharacterEditorState.Editing ?: return
        editorState.form.cleanupPendingAvatar()
    }

    private suspend fun CharacterEditForm.cleanupPendingAvatar() {
        if (avatar.isBlank() || avatar == originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(avatar)
        }
    }
}
