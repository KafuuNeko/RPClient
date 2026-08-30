package me.kafuuneko.rpclient.feature.worldbookedit.presentation

import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEntryListItem
import me.kafuuneko.rpclient.feature.worldbookedit.model.withEntryDisabled

/** 世界书编辑页状态；initialForm 用于统一判断未保存修改。 */
sealed class WorldBookEditUiState {
    data object None : WorldBookEditUiState()

    data class Normal(
        /** 当前流程采用的处理模式。 */
        val mode: WorldBookEditMode,
        /** 当前页面正在编辑的表单数据。 */
        val form: WorldBookEditForm,
        /** 进入编辑页时保存的初始表单快照。 */
        val initialForm: WorldBookEditForm = form,
        /** 世界书编辑页中的条目列表和筛选状态。 */
        val entryListState: WorldBookEntryListState = WorldBookEntryListState.from(form.entries),
        /** 当前页面数据库或资源操作的加载状态。 */
        val loadState: WorldBookEditLoadState = WorldBookEditLoadState.None,
        /** 当前页面互斥展示的对话框状态。 */
        val dialogState: WorldBookEditDialogState = WorldBookEditDialogState.None
    ) : WorldBookEditUiState()

    data class Finished(val previous: WorldBookEditUiState) : WorldBookEditUiState()

    companion object {
        fun finished(previous: WorldBookEditUiState): WorldBookEditUiState {
            if (previous is Finished) return previous
            return Finished(previous)
        }
    }
}

/** 世界书条目列表的查询条件和最终可渲染结果。 */
data class WorldBookEntryListState(
    /** 当前列表或对话框使用的搜索关键词。 */
    val query: String = "",
    /** 当前列表或选择器采用的条目筛选条件。 */
    val filter: WorldBookEntryFilter = WorldBookEntryFilter.All,
    /** 按搜索和筛选条件处理后实际展示的世界书条目。 */
    val visibleEntries: List<WorldBookEntryListItem> = emptyList(),
    /** 当前查询或统计包含的总数量。 */
    val totalCount: Int = 0,
    /** 当前列表中处于启用状态的条目数量。 */
    val activeCount: Int = 0
) {
    fun rebuild(entries: List<WorldBookEntryListItem>): WorldBookEntryListState {
        return copy(
            visibleEntries = entries.filter(::matches),
            totalCount = entries.size,
            activeCount = entries.count { !it.disabled }
        )
    }

    private fun matches(entry: WorldBookEntryListItem): Boolean {
        val matchesQuery = query.isBlank() ||
            entry.name.contains(query, ignoreCase = true) ||
            entry.keywords.any { it.contains(query, ignoreCase = true) }
        val matchesFilter = when (filter) {
            WorldBookEntryFilter.All -> true
            WorldBookEntryFilter.Constant -> entry.constant
            WorldBookEntryFilter.Enabled -> !entry.disabled
            WorldBookEntryFilter.Disabled -> entry.disabled
        }
        return matchesQuery && matchesFilter
    }

    companion object {
        fun from(entries: List<WorldBookEntryListItem>): WorldBookEntryListState {
            return WorldBookEntryListState().rebuild(entries)
        }
    }
}

/** 世界书条目列表支持的互斥过滤条件。 */
enum class WorldBookEntryFilter {
    All,
    Constant,
    Enabled,
    Disabled
}

/** 将已持久化的条目状态同步到表单、基线和列表派生状态。 */
fun WorldBookEditUiState.Normal.withPersistedEntryDisabled(
    entryId: Long,
    disabled: Boolean
): WorldBookEditUiState.Normal {
    val updatedForm = form.withEntryDisabled(entryId, disabled)
    return copy(
        form = updatedForm,
        initialForm = initialForm.withEntryDisabled(entryId, disabled),
        entryListState = entryListState.rebuild(updatedForm.entries)
    )
}

/** 世界书编辑页的创建或编辑模式。 */
enum class WorldBookEditMode {
    Create,
    Edit
}

/** 世界书编辑页当前执行的持久化操作。 */
sealed class WorldBookEditLoadState {
    data object None : WorldBookEditLoadState()
    data object Loading : WorldBookEditLoadState()
    data object Saving : WorldBookEditLoadState()
    data object Deleting : WorldBookEditLoadState()
}

/** 世界书编辑页当前显示的确认对话框。 */
sealed class WorldBookEditDialogState {
    data object None : WorldBookEditDialogState()

    data class DeleteConfirm(
        val worldBookName: String
    ) : WorldBookEditDialogState()

    data object UnsavedChangesConfirm : WorldBookEditDialogState()
}
