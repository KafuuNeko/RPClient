package me.kafuuneko.rpclient.feature.worldbookedit.presentation

import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookBudgetMode

/** 世界书元数据编辑页的用户意图。 */
sealed class WorldBookEditUiIntent {
    data class Init(val lorebookId: Long?) : WorldBookEditUiIntent()

    data object Resume : WorldBookEditUiIntent()

    data object Back : WorldBookEditUiIntent()

    data class ChangeName(val value: String) : WorldBookEditUiIntent()

    data class SelectTokenBudgetMode(val mode: WorldBookBudgetMode) : WorldBookEditUiIntent()

    data class ChangeTokenBudgetTokens(val value: String) : WorldBookEditUiIntent()

    data class ChangeEntrySearchQuery(val value: String) : WorldBookEditUiIntent()

    data class SelectEntryFilter(val filter: WorldBookEntryFilter) : WorldBookEditUiIntent()

    data object AddEntry : WorldBookEditUiIntent()

    data class EditEntry(val entryId: Long) : WorldBookEditUiIntent()

    data class ToggleEntryDisabled(val entryId: Long, val disabled: Boolean) :
        WorldBookEditUiIntent()

    data object SaveWorldBook : WorldBookEditUiIntent()

    data object DeleteWorldBookClick : WorldBookEditUiIntent()

    data object ConfirmDeleteWorldBook : WorldBookEditUiIntent()

    data object ConfirmDiscardChanges : WorldBookEditUiIntent()

    data object DismissDialog : WorldBookEditUiIntent()
}
