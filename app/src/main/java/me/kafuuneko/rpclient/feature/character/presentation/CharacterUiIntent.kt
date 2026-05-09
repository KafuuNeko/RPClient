package me.kafuuneko.rpclient.feature.character.presentation

import android.net.Uri

sealed class CharacterUiIntent {
    data object Init : CharacterUiIntent()

    data object Resume : CharacterUiIntent()

    data object Back : CharacterUiIntent()

    data class ChangeSearchText(val value: String) : CharacterUiIntent()

    data class SelectCharacter(val characterId: Long) : CharacterUiIntent()

    data object CreateCharacter : CharacterUiIntent()

    data class ChangeName(val value: String) : CharacterUiIntent()

    data object PickAvatarClick : CharacterUiIntent()

    data class AvatarSelected(val uri: Uri) : CharacterUiIntent()

    data class ChangeTagsText(val value: String) : CharacterUiIntent()

    data class ChangeDescription(val value: String) : CharacterUiIntent()

    data class ChangePersonality(val value: String) : CharacterUiIntent()

    data class ChangeScenario(val value: String) : CharacterUiIntent()

    data class ChangeFirstMessages(val value: String) : CharacterUiIntent()

    data class ChangeExamplesOfDialogue(val value: String) : CharacterUiIntent()

    data class ChangePostHistoryInstructions(val value: String) : CharacterUiIntent()

    data object SaveCharacter : CharacterUiIntent()

    data object DeleteCharacterClick : CharacterUiIntent()

    data object ConfirmDeleteCharacter : CharacterUiIntent()

    data object DismissDialog : CharacterUiIntent()
}
