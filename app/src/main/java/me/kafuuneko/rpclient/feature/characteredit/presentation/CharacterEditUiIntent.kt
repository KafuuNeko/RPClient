package me.kafuuneko.rpclient.feature.characteredit.presentation

/** 角色创建/编辑页面的字段变更、文件选择、保存与删除意图。 */
sealed class CharacterEditUiIntent {
    data class Init(val characterId: Long?) : CharacterEditUiIntent()

    data object Resume : CharacterEditUiIntent()

    data object Back : CharacterEditUiIntent()

    data object PickAvatarClick : CharacterEditUiIntent()

    data class AvatarCropped(val fileUuid: String) : CharacterEditUiIntent()

    data class UpdateCharacterLorebook(val lorebookId: Long) : CharacterEditUiIntent()

    data class SelectLLMProvider(val providerId: Long) : CharacterEditUiIntent()

    data object OpenWorldBookManager : CharacterEditUiIntent()

    data class ChangeName(val value: String) : CharacterEditUiIntent()

    data class SetTags(val tags: List<String>) : CharacterEditUiIntent()

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

    data class ShowPromptEditor(val field: CharacterPromptField) : CharacterEditUiIntent()

    data class ChangePromptEditorDraft(val value: String) : CharacterEditUiIntent()

    data object CopyPromptEditorText : CharacterEditUiIntent()

    data object ConfirmPromptEditor : CharacterEditUiIntent()

    data object SaveCharacter : CharacterEditUiIntent()

    data object DeleteCharacterClick : CharacterEditUiIntent()

    data object ConfirmDeleteCharacter : CharacterEditUiIntent()

    data object ConfirmDeleteCharacterOnly : CharacterEditUiIntent()

    data object ConfirmDeleteCharacterWithLorebook : CharacterEditUiIntent()

    data object ConfirmDiscardChanges : CharacterEditUiIntent()

    data object DismissDialog : CharacterEditUiIntent()
}
