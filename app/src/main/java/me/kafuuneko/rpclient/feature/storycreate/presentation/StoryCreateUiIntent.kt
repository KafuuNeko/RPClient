package me.kafuuneko.rpclient.feature.storycreate.presentation

import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterActivationMode

/** 新建 Story 页面的表单操作和提交意图。 */
sealed class StoryCreateUiIntent {
    data object Init : StoryCreateUiIntent()

    data object Back : StoryCreateUiIntent()

    data class ChangeTitle(val value: String) : StoryCreateUiIntent()

    data class SelectPersona(val id: Long) : StoryCreateUiIntent()

    data object ShowCharacterPicker : StoryCreateUiIntent()

    data class ChangeCharacterQuery(val value: String) : StoryCreateUiIntent()

    data class VisiblePickerCharactersChanged(
        val characterIds: Set<Long>,
        val targetSizePx: Int,
    ) : StoryCreateUiIntent()

    data class VisibleFormCharactersChanged(
        val characterIds: Set<Long>,
        val targetSizePx: Int,
    ) : StoryCreateUiIntent()

    data class ToggleCharacterPickerSelectedOnly(val selectedOnly: Boolean) : StoryCreateUiIntent()

    data class ToggleCharacterPickerItem(val characterId: Long) : StoryCreateUiIntent()

    data object ConfirmCharacterPicker : StoryCreateUiIntent()

    data class ToggleCharacter(val characterId: Long) : StoryCreateUiIntent()

    data class SetCharacterActivationMode(
        val characterId: Long,
        val activationMode: StoryCreateCharacterActivationMode
    ) : StoryCreateUiIntent()

    data object ShowLorebookPicker : StoryCreateUiIntent()

    data class ChangeLorebookQuery(val value: String) : StoryCreateUiIntent()

    data class ToggleLorebookPickerSelectedOnly(val selectedOnly: Boolean) : StoryCreateUiIntent()

    data class ToggleLorebook(val lorebookId: Long) : StoryCreateUiIntent()

    data class ToggleLorebookEntry(val entryId: Long) : StoryCreateUiIntent()

    data object ConfirmLorebookPicker : StoryCreateUiIntent()

    data object DismissDialog : StoryCreateUiIntent()

    data object CreateStory : StoryCreateUiIntent()
}
