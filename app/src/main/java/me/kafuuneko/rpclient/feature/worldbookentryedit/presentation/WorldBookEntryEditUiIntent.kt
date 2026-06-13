package me.kafuuneko.rpclient.feature.worldbookentryedit.presentation

/**
 * 世界书条目编辑页的用户意图。
 *
 * 数字输入仍以字符串传递，保证输入过程中的空值和未完成值不会被 UI 层提前丢失。
 */
sealed class WorldBookEntryEditUiIntent {
    data class Init(val lorebookId: Long, val entryId: Long?) : WorldBookEntryEditUiIntent()

    data object Back : WorldBookEntryEditUiIntent()

    data class ChangeName(val value: String) : WorldBookEntryEditUiIntent()

    data object AddKeyword : WorldBookEntryEditUiIntent()

    data class ChangeKeyword(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteKeyword(val index: Int) : WorldBookEntryEditUiIntent()

    data object AddSecondaryKeyword : WorldBookEntryEditUiIntent()

    data class ChangeSecondaryKeyword(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteSecondaryKeyword(val index: Int) : WorldBookEntryEditUiIntent()

    data class ChangeConstant(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeDisabled(val value: Boolean) : WorldBookEntryEditUiIntent()

    data object AddCategory : WorldBookEntryEditUiIntent()

    data class ChangeCategory(val index: Int, val value: String) : WorldBookEntryEditUiIntent()

    data class DeleteCategory(val index: Int) : WorldBookEntryEditUiIntent()

    data class ChangeOrder(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeDepth(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangePosition(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeRole(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeProbability(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeSelectiveLogic(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeIgnoreBudget(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeScanDepth(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeMatchWholeWords(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeCaseSensitive(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangePreventRecursion(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeDelayUntilRecursion(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeSticky(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeCooldown(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeDelay(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeOutletName(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeMatchPersonaDescription(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeMatchCharacterDescription(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeMatchCharacterPersonality(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeMatchCharacterDepthPrompt(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeMatchScenario(val value: Boolean) : WorldBookEntryEditUiIntent()

    data class ChangeExtensionsJson(val value: String) : WorldBookEntryEditUiIntent()

    data class ChangeContent(val value: String) : WorldBookEntryEditUiIntent()

    data object SaveEntry : WorldBookEntryEditUiIntent()

    data object DeleteEntryClick : WorldBookEntryEditUiIntent()

    data object ConfirmDeleteEntry : WorldBookEntryEditUiIntent()

    data object ConfirmDiscardChanges : WorldBookEntryEditUiIntent()

    data object DismissDialog : WorldBookEntryEditUiIntent()
}
