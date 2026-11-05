package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.kafuuneko.rpclient.R

/**
 * 列表多选共用操作区，使用固定触摸尺寸避免长译文挤压窄屏标题。
 * @param allSelected 当前可见项是否全部选中。
 * @param canSelectAll 当前过滤结果是否允许全选。
 * @param enabled 是否允许操作；持久化期间统一禁用。
 * @param onToggleAll 切换当前可见项的全选状态。
 * @param onDelete 请求删除确认。
 * @param exportDescription 导出的无障碍说明。
 * @param onExport 可选的批量导出入口。
 */
@Composable
fun BatchSelectionActions(
    allSelected: Boolean,
    canSelectAll: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    exportDescription: String? = null,
    onExport: (() -> Unit)? = null,
) {
    // 全选与导出沿用普通操作色，删除使用主题错误色提示其破坏性。
    IconButton(onClick = onToggleAll, enabled = enabled && canSelectAll) {
        Icon(
            imageVector = if (allSelected) Icons.Rounded.CheckBox else Icons.Rounded.SelectAll,
            contentDescription = stringResource(if (allSelected) R.string.selection_deselect_all else R.string.selection_select_all),
        )
    }
    if (onExport != null) {
        IconButton(onClick = onExport, enabled = enabled) {
            Icon(imageVector = Icons.Rounded.FileUpload, contentDescription = exportDescription)
        }
    }
    IconButton(
        onClick = onDelete,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.delete_selected_items_title)
        )
    }
}

/** 多选卡片中的只读勾选标记，交互与无障碍选中语义由整张卡片承载。 */
@Composable
fun BatchSelectionIndicator(selected: Boolean) {
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
        contentDescription = null,
        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
