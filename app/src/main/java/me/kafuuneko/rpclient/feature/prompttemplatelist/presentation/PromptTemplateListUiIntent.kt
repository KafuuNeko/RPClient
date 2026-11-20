package me.kafuuneko.rpclient.feature.prompttemplatelist.presentation

import android.net.Uri

/** 用户操作只表达业务意图，持久化交给 Repository。 */
sealed class PromptTemplateListUiIntent {
    data object Init : PromptTemplateListUiIntent()
    data object Retry : PromptTemplateListUiIntent()
    data object Back : PromptTemplateListUiIntent()
    data object Create : PromptTemplateListUiIntent()
    data object ImportClick : PromptTemplateListUiIntent()
    data class ExportClick(val id: Long) : PromptTemplateListUiIntent()
    data class ImportResult(val uri: Uri?) : PromptTemplateListUiIntent()
    data class ExportResult(val uri: Uri?) : PromptTemplateListUiIntent()
    data class Open(val id: Long) : PromptTemplateListUiIntent()
    data class Activate(val id: Long) : PromptTemplateListUiIntent()
    data class Copy(val id: Long) : PromptTemplateListUiIntent()
    data class Rename(val id: Long) : PromptTemplateListUiIntent()
    data class Delete(val id: Long) : PromptTemplateListUiIntent()
    data class ChangeName(val value: String) : PromptTemplateListUiIntent()
    data class ChangeDescription(val value: String) : PromptTemplateListUiIntent()
    data object SaveMetadata : PromptTemplateListUiIntent()
    data object ConfirmDelete : PromptTemplateListUiIntent()
    data object DismissDialog : PromptTemplateListUiIntent()
}
