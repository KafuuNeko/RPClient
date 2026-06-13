package me.kafuuneko.rpclient.feature.worldbooklist.presentation

/** 世界书列表页的用户意图与系统文件选择结果。 */
sealed class WorldBookListUiIntent {
    data object Init : WorldBookListUiIntent()

    data object Resume : WorldBookListUiIntent()

    data object Back : WorldBookListUiIntent()

    data object CreateWorldBook : WorldBookListUiIntent()

    data class EditWorldBook(val lorebookId: Long) : WorldBookListUiIntent()

    data object ImportWorldBookClick : WorldBookListUiIntent()

    data class ImportWorldBook(val uri: android.net.Uri) : WorldBookListUiIntent()

    data class ExportWorldBookClick(val lorebookId: Long) : WorldBookListUiIntent()

    data class ExportWorldBook(val lorebookId: Long, val uri: android.net.Uri) : WorldBookListUiIntent()
}
