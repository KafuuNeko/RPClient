package me.kafuuneko.rpclient.feature.storycreate.presentation

import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterActivationMode

/** 新建 Story 页面的表单操作和提交意图。 */
sealed class StoryCreateUiIntent {
    data object Init : StoryCreateUiIntent()

    data object Back : StoryCreateUiIntent()

    data class ChangeTitle(val value: String) : StoryCreateUiIntent()

    data class SetIncludeUserPersona(val enabled: Boolean) : StoryCreateUiIntent()

    data class ChangeCharacterQuery(val value: String) : StoryCreateUiIntent()

    data class ToggleCharacter(val characterId: Long) : StoryCreateUiIntent()

    data class SetCharacterActivationMode(
        val characterId: Long,
        val activationMode: StoryCreateCharacterActivationMode
    ) : StoryCreateUiIntent()

    data class ChangeLorebookQuery(val value: String) : StoryCreateUiIntent()

    data class ToggleLorebook(val lorebookId: Long) : StoryCreateUiIntent()

    data class ToggleLorebookEntry(val entryId: Long) : StoryCreateUiIntent()

    data object CreateStory : StoryCreateUiIntent()
}
