package me.kafuuneko.rpclient.feature.characteredit

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditViewEvent
import me.kafuuneko.rpclient.libs.core.AppViewEvent
import me.kafuuneko.rpclient.libs.core.CoreViewModelWithEvent
import me.kafuuneko.rpclient.libs.core.UiIntentObserver
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.repository.CharacterRepository
import me.kafuuneko.rpclient.libs.room.repository.FileRepository
import me.kafuuneko.rpclient.libs.utils.orSingleBlank
import me.kafuuneko.rpclient.libs.utils.removeAtOrSelf
import me.kafuuneko.rpclient.libs.utils.trimmedNotBlank
import me.kafuuneko.rpclient.libs.utils.updateAt
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CharacterEditViewModel : CoreViewModelWithEvent<CharacterEditUiIntent, CharacterEditUiState>(
    CharacterEditUiState.None
), KoinComponent {
    private val mCharacterRepository by inject<CharacterRepository>()
    private val mFileRepository by inject<FileRepository>()

    @UiIntentObserver(CharacterEditUiIntent.Init::class)
    private suspend fun onInit(intent: CharacterEditUiIntent.Init) {
        if (!isStateOf<CharacterEditUiState.None>()) return
        CharacterEditUiState.Normal(
            mode = if (intent.characterId == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = CharacterEditForm(),
            loadState = CharacterEditLoadState.Loading
        ).setup()
        val character = intent.characterId?.let {
            withContext(Dispatchers.IO) { mCharacterRepository.getCharacterById(it) }
        }
        val form = character?.let { CharacterEditForm.from(it) } ?: CharacterEditForm()
        CharacterEditUiState.Normal(
            mode = if (character == null) CharacterEditMode.Create else CharacterEditMode.Edit,
            form = form.ensureListInputs(),
            avatarFilePath = form.resolveAvatarPath()
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.Back::class)
    private suspend fun onBack() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (
            uiState.loadState == CharacterEditLoadState.Saving ||
            uiState.loadState == CharacterEditLoadState.Deleting
        ) return
        if (uiState.form.hasUnsavedChangesFrom(uiState.initialForm)) {
            uiState.copy(dialogState = CharacterEditDialogState.UnsavedChangesConfirm).setup()
            return
        }
        finishEditing()
    }

    @UiIntentObserver(CharacterEditUiIntent.PickAvatarClick::class)
    private fun onPickAvatarClick() {
        if (!isStateOf<CharacterEditUiState.Normal>()) return
        CharacterEditViewEvent.OpenAvatarPicker.tryEmit()
    }

    @UiIntentObserver(CharacterEditUiIntent.AvatarSelected::class)
    private suspend fun onAvatarSelected(intent: CharacterEditUiIntent.AvatarSelected) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        val avatarUuid = runCatching {
            withContext(Dispatchers.IO) {
                val uuid = mFileRepository.saveFile(intent.uri)
                if (
                    uiState.form.avatar.isNotBlank() &&
                    uiState.form.avatar != uiState.form.originalAvatar &&
                    uiState.form.avatar != uuid
                ) {
                    mFileRepository.deleteFile(uiState.form.avatar)
                }
                uuid
            }
        }.getOrElse {
            val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
            latestState.copy(loadState = CharacterEditLoadState.None).setup()
            AppViewEvent.PopupToastMessageByResId(R.string.character_avatar_save_failed).tryEmit()
            return
        }
        val latestState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val form = latestState.form.copy(avatar = avatarUuid)
        latestState.copy(
            form = form,
            avatarFilePath = form.resolveAvatarPath(),
            loadState = CharacterEditLoadState.None
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ChangeName::class)
    private fun onChangeName(intent: CharacterEditUiIntent.ChangeName) =
        updateForm { copy(name = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddTag::class)
    private fun onAddTag() =
        updateForm { copy(tags = tags + "") }

    @UiIntentObserver(CharacterEditUiIntent.ChangeTag::class)
    private fun onChangeTag(intent: CharacterEditUiIntent.ChangeTag) =
        updateForm { copy(tags = tags.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteTag::class)
    private fun onDeleteTag(intent: CharacterEditUiIntent.DeleteTag) =
        updateForm { copy(tags = tags.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDescription::class)
    private fun onChangeDescription(intent: CharacterEditUiIntent.ChangeDescription) =
        updateForm { copy(description = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCreatorNotes::class)
    private fun onChangeCreatorNotes(intent: CharacterEditUiIntent.ChangeCreatorNotes) =
        updateForm { copy(creatorNotes = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCreator::class)
    private fun onChangeCreator(intent: CharacterEditUiIntent.ChangeCreator) =
        updateForm { copy(creator = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeCharacterVersion::class)
    private fun onChangeCharacterVersion(intent: CharacterEditUiIntent.ChangeCharacterVersion) =
        updateForm { copy(characterVersion = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangePersonality::class)
    private fun onChangePersonality(intent: CharacterEditUiIntent.ChangePersonality) =
        updateForm { copy(personality = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeScenario::class)
    private fun onChangeScenario(intent: CharacterEditUiIntent.ChangeScenario) =
        updateForm { copy(scenario = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddFirstMessage::class)
    private fun onAddFirstMessage() =
        updateForm { copy(firstMessages = firstMessages + "") }

    @UiIntentObserver(CharacterEditUiIntent.ChangeFirstMessage::class)
    private fun onChangeFirstMessage(intent: CharacterEditUiIntent.ChangeFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteFirstMessage::class)
    private fun onDeleteFirstMessage(intent: CharacterEditUiIntent.DeleteFirstMessage) =
        updateForm { copy(firstMessages = firstMessages.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeExamplesOfDialogue::class)
    private fun onChangeExamplesOfDialogue(intent: CharacterEditUiIntent.ChangeExamplesOfDialogue) =
        updateForm { copy(examplesOfDialogue = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangePostHistoryInstructions::class)
    private fun onChangePostHistoryInstructions(intent: CharacterEditUiIntent.ChangePostHistoryInstructions) =
        updateForm { copy(postHistoryInstructions = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeSystemPrompt::class)
    private fun onChangeSystemPrompt(intent: CharacterEditUiIntent.ChangeSystemPrompt) =
        updateForm { copy(systemPrompt = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptPrompt::class)
    private fun onChangeDepthPromptPrompt(intent: CharacterEditUiIntent.ChangeDepthPromptPrompt) =
        updateForm { copy(depthPromptPrompt = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptDepth::class)
    private fun onChangeDepthPromptDepth(intent: CharacterEditUiIntent.ChangeDepthPromptDepth) =
        updateForm { copy(depthPromptDepth = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeDepthPromptRole::class)
    private fun onChangeDepthPromptRole(intent: CharacterEditUiIntent.ChangeDepthPromptRole) =
        updateForm { copy(depthPromptRole = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.AddAlternateGreeting::class)
    private fun onAddAlternateGreeting() =
        updateForm { copy(alternateGreetings = alternateGreetings + "") }

    @UiIntentObserver(CharacterEditUiIntent.ChangeAlternateGreeting::class)
    private fun onChangeAlternateGreeting(intent: CharacterEditUiIntent.ChangeAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.updateAt(intent.index, intent.value)) }

    @UiIntentObserver(CharacterEditUiIntent.DeleteAlternateGreeting::class)
    private fun onDeleteAlternateGreeting(intent: CharacterEditUiIntent.DeleteAlternateGreeting) =
        updateForm { copy(alternateGreetings = alternateGreetings.removeAtOrSelf(intent.index).orSingleBlank()) }

    @UiIntentObserver(CharacterEditUiIntent.ChangeExtensionsJson::class)
    private fun onChangeExtensionsJson(intent: CharacterEditUiIntent.ChangeExtensionsJson) =
        updateForm { copy(extensionsJson = intent.value) }

    @UiIntentObserver(CharacterEditUiIntent.SaveCharacter::class)
    private suspend fun onSaveCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        val character = uiState.form.toCharacterOrNullWithToast() ?: return
        uiState.copy(loadState = CharacterEditLoadState.Saving).setup()
        withContext(Dispatchers.IO) {
            mCharacterRepository.saveCharacter(character)
            if (uiState.form.originalAvatar.isNotBlank() && uiState.form.originalAvatar != character.avatar) {
                mFileRepository.deleteFile(uiState.form.originalAvatar)
            }
        }
        AppViewEvent.PopupToastMessageByResId(
            if (uiState.mode == CharacterEditMode.Create) R.string.character_created else R.string.character_saved
        ).tryEmit()
        CharacterEditUiState.Finished.setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.DeleteCharacterClick::class)
    private suspend fun onDeleteCharacterClick() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) {
            cleanupPendingAvatar()
            CharacterEditUiState.Finished.setup()
            return
        }
        uiState.copy(
            dialogState = CharacterEditDialogState.DeleteConfirm(
                characterName = uiState.form.name
            )
        ).setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ConfirmDeleteCharacter::class)
    private suspend fun onConfirmDeleteCharacter() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.isNew) return
        uiState.copy(
            loadState = CharacterEditLoadState.Deleting,
            dialogState = CharacterEditDialogState.None
        ).setup()
        val pendingAvatar = uiState.form.avatar
            .takeIf { it.isNotBlank() && it != uiState.form.originalAvatar }
        withContext(Dispatchers.IO) {
            val character = mCharacterRepository.getCharacterById(uiState.form.id)
            mCharacterRepository.deleteCharacter(uiState.form.id)
            character?.avatar?.takeIf { it.isNotBlank() }?.let {
                mFileRepository.deleteFile(it)
            }
            pendingAvatar?.let {
                mFileRepository.deleteFile(it)
            }
        }
        AppViewEvent.PopupToastMessageByResId(R.string.character_deleted).tryEmit()
        CharacterEditUiState.Finished.setup()
    }

    @UiIntentObserver(CharacterEditUiIntent.ConfirmDiscardChanges::class)
    private suspend fun onConfirmDiscardChanges() {
        finishEditing()
    }

    @UiIntentObserver(CharacterEditUiIntent.DismissDialog::class)
    private fun onDismissDialog() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(dialogState = CharacterEditDialogState.None).setup()
    }

    private fun updateForm(block: CharacterEditForm.() -> CharacterEditForm) {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        uiState.copy(form = uiState.form.block()).setup()
    }

    private fun CharacterEditForm.toCharacterOrNullWithToast(): Character? {
        if (name.isBlank()) {
            AppViewEvent.PopupToastMessageByResId(R.string.character_name_empty).tryEmit()
            return null
        }
        return toCharacter()
    }

    private suspend fun CharacterEditForm.resolveAvatarPath(): String? {
        return avatar.takeIf { it.isNotBlank() }?.let {
            withContext(Dispatchers.IO) { mFileRepository.getFile(it)?.absolutePath }
        }
    }

    private suspend fun cleanupPendingAvatar() {
        val uiState = getOrNull<CharacterEditUiState.Normal>() ?: return
        if (uiState.form.avatar.isBlank() || uiState.form.avatar == uiState.form.originalAvatar) return
        withContext(Dispatchers.IO) {
            mFileRepository.deleteFile(uiState.form.avatar)
        }
    }

    private suspend fun finishEditing() {
        cleanupPendingAvatar()
        CharacterEditUiState.Finished.setup()
    }

    private fun CharacterEditForm.hasUnsavedChangesFrom(initialForm: CharacterEditForm): Boolean {
        return toComparableForm() != initialForm.toComparableForm()
    }

    private fun CharacterEditForm.ensureListInputs(): CharacterEditForm {
        return copy(
            tags = tags.orSingleBlank(),
            firstMessages = firstMessages.orSingleBlank(),
            alternateGreetings = alternateGreetings.orSingleBlank()
        )
    }

    private fun CharacterEditForm.toComparableForm(): CharacterEditForm {
        return copy(
            name = name.trim(),
            avatar = avatar.trim(),
            originalAvatar = originalAvatar.trim(),
            tags = tags.trimmedNotBlank(),
            description = description.trim(),
            creatorNotes = creatorNotes.trim(),
            creator = creator.trim(),
            characterVersion = characterVersion.trim(),
            personality = personality.trim(),
            scenario = scenario.trim(),
            firstMessages = firstMessages.trimmedNotBlank(),
            examplesOfDialogue = examplesOfDialogue.trim(),
            postHistoryInstructions = postHistoryInstructions.trim(),
            systemPrompt = systemPrompt.trim(),
            alternateGreetings = alternateGreetings.trimmedNotBlank(),
            extensionsJson = extensionsJson.trim().ifBlank { "{}" },
            depthPromptPrompt = depthPromptPrompt.trim(),
            depthPromptDepth = depthPromptDepth.trim(),
            depthPromptRole = depthPromptRole.trim()
        )
    }

}
