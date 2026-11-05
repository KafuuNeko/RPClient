package me.kafuuneko.rpclient.feature.worldbooklist.presentation

import android.net.Uri

/** 世界书列表页的用户意图与系统文件选择结果。 */
sealed class WorldBookListUiIntent {
    data object Init : WorldBookListUiIntent()

    data object Resume : WorldBookListUiIntent()

    data object Back : WorldBookListUiIntent()

    data object CreateWorldBook : WorldBookListUiIntent()

    data class EditWorldBook(val lorebookId: Long) : WorldBookListUiIntent()

    data class BeginWorldBookSelection(val lorebookId: Long) : WorldBookListUiIntent()

    data class ToggleWorldBookSelection(val lorebookId: Long) : WorldBookListUiIntent()

    data object ToggleAllWorldBookSelection : WorldBookListUiIntent()

    data object ClearWorldBookSelection : WorldBookListUiIntent()

    data object DeleteSelectedWorldBooksClick : WorldBookListUiIntent()

    data object ConfirmDeleteSelectedWorldBooks : WorldBookListUiIntent()

    data object ExportSelectedWorldBooksClick : WorldBookListUiIntent()

    data class ExportSelectedWorldBooks(val lorebookIds: List<Long>, val uri: Uri) :
        WorldBookListUiIntent()

    data object DismissDialog : WorldBookListUiIntent()

    data object ImportWorldBookClick : WorldBookListUiIntent()

    data class ImportWorldBooks(val uris: List<Uri>) : WorldBookListUiIntent()

    data object ImportWithGlobalBudget : WorldBookListUiIntent()

    data object ImportWithOriginalBudget : WorldBookListUiIntent()

    data class ExportWorldBookClick(val lorebookId: Long) : WorldBookListUiIntent()

    data class ExportWorldBook(val lorebookId: Long, val uri: Uri) :
        WorldBookListUiIntent()
}
