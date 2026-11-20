package me.kafuuneko.rpclient.feature.memoryedit.presentation

import android.net.Uri
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditEntryItem
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryStatus

/** 用户操作交给 ViewModel 校验归属，UI 不访问持久化数据。 */
sealed class MemoryEditUiIntent {
    data class Init(val type: String?, val id: Long) : MemoryEditUiIntent()
    data object Back : MemoryEditUiIntent()
    data object Refresh : MemoryEditUiIntent()
    data class SelectTab(val tab: MemoryEditTab) : MemoryEditUiIntent()
    data object Create : MemoryEditUiIntent()
    data class Edit(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class ChangeDraft(val draft: MemoryDraft) : MemoryEditUiIntent()
    data object Save : MemoryEditUiIntent()
    data object Reload : MemoryEditUiIntent()
    data class Confirm(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class ShowDeleteDialog(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data object ConfirmDelete : MemoryEditUiIntent()
    data class ShowHistoryDialog(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class Override(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class ShowScopeDialog(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class ChangeScope(val personaId: Long?, val privateId: Long?) : MemoryEditUiIntent()
    data object ConfirmScope : MemoryEditUiIntent()
    data class ShowPromoteDialog(val entry: MemoryEditEntryItem) : MemoryEditUiIntent()
    data class ChangePromotion(val characterId: Long) : MemoryEditUiIntent()
    data object ConfirmPromotion : MemoryEditUiIntent()
    data class Filter(
        val status: MemoryStatus?,
        val persona: Long,
        val audience: Long,
    ) : MemoryEditUiIntent()

    data class ChangeSettings(val inherit: Boolean, val recall: Boolean) : MemoryEditUiIntent()
    data object DismissDialog : MemoryEditUiIntent()
    data object ConfirmDiscard : MemoryEditUiIntent()
    data object ImportClick : MemoryEditUiIntent()
    data object ExportClick : MemoryEditUiIntent()
    data class ImportResult(val uri: Uri?) : MemoryEditUiIntent()
    data class ExportResult(val uri: Uri?) : MemoryEditUiIntent()
    data class MapPersona(val source: Long, val target: Long) : MemoryEditUiIntent()
    data class MapMember(val source: Long, val target: Long) : MemoryEditUiIntent()
    data object ConfirmImport : MemoryEditUiIntent()
}
