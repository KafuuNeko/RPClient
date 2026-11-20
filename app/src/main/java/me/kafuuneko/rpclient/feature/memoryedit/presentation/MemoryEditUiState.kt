package me.kafuuneko.rpclient.feature.memoryedit.presentation

import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditEntryItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditImportMappingItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditInheritedItem
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditOwnerUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditRevisionUiModel
import me.kafuuneko.rpclient.feature.memoryedit.model.MemoryEditSettingsUiModel
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTarget

/** 编辑器状态按标题、列表、设置和互斥弹窗分层，保存失败保留原草稿。 */
sealed class MemoryEditUiState {
    data object None : MemoryEditUiState()

    data class Normal(
        val target: MemoryTarget,
        val title: String = "",
        val content: MemoryEditContentState = MemoryEditContentState(),
        val settings: MemoryEditSettingsUiModel? = null,
        val personas: List<MemoryEditOwnerUiModel> = emptyList(),
        val dialog: MemoryEditDialogState = MemoryEditDialogState.None,
        val busy: Boolean = false,
        val choosingDocument: Boolean = false,
    ) : MemoryEditUiState()

    data class Finished(val previous: MemoryEditUiState) : MemoryEditUiState()
}

/** 单聊与群聊记忆页的分段视图。 */
enum class MemoryEditTab {
    Scope,
    Inherited,
}

/** 当前范围与只读继承区分别渲染，筛选条件不会改变底层授权。 */
data class MemoryEditContentState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val totalCount: Int = 0,
    val tab: MemoryEditTab = MemoryEditTab.Scope,
    val entries: List<MemoryEditEntryItem> = emptyList(),
    val inherited: List<MemoryEditInheritedItem> = emptyList(),
    val status: MemoryStatus? = null,
    val persona: Long = -1,
    val audience: Long = -1,
)

/** 对正文、范围、删除及导入分别确认，避免误扩大可见范围。 */
sealed class MemoryEditDialogState {
    data object None : MemoryEditDialogState()

    data class Edit(
        val entry: MemoryEditEntryItem?,
        val draft: MemoryDraft,
        val initial: MemoryDraft = draft,
    ) : MemoryEditDialogState()

    data class Delete(val entry: MemoryEditEntryItem) : MemoryEditDialogState()

    data class History(
        val entry: MemoryEditEntryItem,
        val revisions: List<MemoryEditRevisionUiModel>,
    ) : MemoryEditDialogState()

    data class Scope(
        val entry: MemoryEditEntryItem,
        val personaId: Long?,
        val privateId: Long?,
    ) : MemoryEditDialogState()

    data class Promote(
        val entry: MemoryEditEntryItem,
        val eligibleCharacters: List<MemoryEditOwnerUiModel>,
        val characterId: Long?,
    ) : MemoryEditDialogState()

    data class Import(
        val entryCount: Int,
        val requiredPersonas: List<MemoryEditImportMappingItem>,
        val requiredMembers: List<MemoryEditImportMappingItem>,
        val personas: Map<Long, Long> = emptyMap(),
        val members: Map<Long, Long> = emptyMap(),
        val isComplete: Boolean = false,
    ) : MemoryEditDialogState()

    data class Discard(val editor: Edit) : MemoryEditDialogState()
}
