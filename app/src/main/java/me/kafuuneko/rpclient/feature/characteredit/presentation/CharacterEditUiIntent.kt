package me.kafuuneko.rpclient.feature.characteredit.presentation

import android.net.Uri

sealed class CharacterEditUiIntent {
    data class Init(val characterId: Long?) : CharacterEditUiIntent()

    data object Back : CharacterEditUiIntent()

    data object PickAvatarClick : CharacterEditUiIntent()

    data class AvatarSelected(val uri: Uri) : CharacterEditUiIntent()

    data class UpdateCharacterLorebook(val lorebookId: Long) : CharacterEditUiIntent()

    data class ChangeName(val value: String) : CharacterEditUiIntent()

    data object AddTag : CharacterEditUiIntent()

    data class ChangeTag(val index: Int, val value: String) : CharacterEditUiIntent()

    data class DeleteTag(val index: Int) : CharacterEditUiIntent()

    data class ChangeDescription(val value: String) : CharacterEditUiIntent()

    data class ChangeCreatorNotes(val value: String) : CharacterEditUiIntent()

    data class ChangeCreator(val value: String) : CharacterEditUiIntent()

    data class ChangeCharacterVersion(val value: String) : CharacterEditUiIntent()

    data class ChangePersonality(val value: String) : CharacterEditUiIntent()

    data class ChangeScenario(val value: String) : CharacterEditUiIntent()

    data object AddFirstMessage : CharacterEditUiIntent()

    data class ChangeFirstMessage(val index: Int, val value: String) : CharacterEditUiIntent()

    data class DeleteFirstMessage(val index: Int) : CharacterEditUiIntent()

    data class ChangeExamplesOfDialogue(val value: String) : CharacterEditUiIntent()

    data class ChangePostHistoryInstructions(val value: String) : CharacterEditUiIntent()

    data class ChangeSystemPrompt(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptPrompt(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptDepth(val value: String) : CharacterEditUiIntent()

    data class ChangeDepthPromptRole(val value: String) : CharacterEditUiIntent()

    data object AddAlternateGreeting : CharacterEditUiIntent()

    data class ChangeAlternateGreeting(val index: Int, val value: String) : CharacterEditUiIntent()

    data class DeleteAlternateGreeting(val index: Int) : CharacterEditUiIntent()

    data class ChangeExtensionsJson(val value: String) : CharacterEditUiIntent()

    data object SaveCharacter : CharacterEditUiIntent()

    data object DeleteCharacterClick : CharacterEditUiIntent()

    data object ConfirmDeleteCharacter : CharacterEditUiIntent()

    data object ConfirmDiscardChanges : CharacterEditUiIntent()

    data object DismissDialog : CharacterEditUiIntent()
}
