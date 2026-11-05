package me.kafuuneko.rpclient.feature.worldbooklist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbooklist.model.WorldBookListItem
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListDialogState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListLoadState
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiIntent
import me.kafuuneko.rpclient.feature.worldbooklist.presentation.WorldBookListUiState
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.AppWarningDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.BatchSelectionActions
import me.kafuuneko.rpclient.ui.widgets.BatchSelectionIndicator
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor

/** 世界书列表与文件导入导出操作的 Compose 入口。 */
@Composable
fun WorldBookListLayout(
    uiState: WorldBookListUiState,
    emit: WorldBookListUiIntent.() -> Unit,
) {
    BackHandler(
        enabled = uiState is WorldBookListUiState.Normal,
        onBack = { WorldBookListUiIntent.Back.emit() })
    when (uiState) {
        WorldBookListUiState.None -> Unit
        is WorldBookListUiState.Finished -> WorldBookListLayout(
            uiState = uiState.previous,
            emit = {})

        is WorldBookListUiState.Normal -> WorldBookListNormal(uiState, emit)
    }
}

@Composable
private fun WorldBookListNormal(
    state: WorldBookListUiState.Normal,
    emit: WorldBookListUiIntent.() -> Unit,
) {
    // 多选只替换顶部操作和卡片尾部，保留原列表布局与滚动位置。
    val selectionMode = state.selectedLorebookIds.isNotEmpty()
    val enabled = state.loadState == WorldBookListLoadState.None
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = if (selectionMode) stringResource(
                R.string.selected_count,
                state.selectedLorebookIds.size
            )
            else stringResource(R.string.world_book_manager),
            onBack = { WorldBookListUiIntent.Back.emit() },
            actions = {
                if (selectionMode) {
                    BatchSelectionActions(
                        allSelected = state.lorebooks.all { it.id in state.selectedLorebookIds },
                        canSelectAll = state.lorebooks.isNotEmpty(),
                        enabled = enabled,
                        onToggleAll = { WorldBookListUiIntent.ToggleAllWorldBookSelection.emit() },
                        onDelete = { WorldBookListUiIntent.DeleteSelectedWorldBooksClick.emit() },
                        exportDescription = stringResource(R.string.world_book_export_selected),
                        onExport = { WorldBookListUiIntent.ExportSelectedWorldBooksClick.emit() },
                    )
                } else {
                    IconButton(
                        enabled = enabled,
                        onClick = { WorldBookListUiIntent.ImportWorldBookClick.emit() }) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.import_world_book),
                        )
                    }
                    IconButton(
                        enabled = enabled,
                        onClick = { WorldBookListUiIntent.CreateWorldBook.emit() }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.create_world_book),
                        )
                    }
                }
            },
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 8.dp,
                end = 18.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RpSectionHeader(
                    title = stringResource(R.string.world_book_library),
                    action = if (selectionMode || !enabled) null else stringResource(R.string.create),
                    onAction = { WorldBookListUiIntent.CreateWorldBook.emit() },
                )
            }
            if (!enabled) {
                item { LoadingRow(state.loadState) }
            } else if (state.lorebooks.isEmpty()) {
                item { EmptyPanel() }
            }
            items(items = state.lorebooks, key = { it.id }) { lorebook ->
                WorldBookCard(
                    lorebook = lorebook,
                    selectionMode = selectionMode,
                    selected = lorebook.id in state.selectedLorebookIds,
                    enabled = enabled,
                    onLongClick = {
                        WorldBookListUiIntent.BeginWorldBookSelection(lorebook.id).emit()
                    },
                    onClick = { WorldBookListUiIntent.EditWorldBook(lorebook.id).emit() },
                    onExport = { WorldBookListUiIntent.ExportWorldBookClick(lorebook.id).emit() },
                )
            }
        }
    }
    DialogSwitch(state.dialogState, emit)
}

@Composable
private fun DialogSwitch(
    dialogState: WorldBookListDialogState,
    emit: WorldBookListUiIntent.() -> Unit,
) {
    when (dialogState) {
        // 结果和删除确认复用应用级弹窗，预算确认沿用现有导入策略。
        WorldBookListDialogState.None -> Unit
        is WorldBookListDialogState.DeleteSelectedConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_selected_items_title),
            message = stringResource(
                R.string.world_book_batch_delete_message,
                dialogState.lorebookIds.size
            ),
            onConfirm = { WorldBookListUiIntent.ConfirmDeleteSelectedWorldBooks.emit() },
        )

        is WorldBookListDialogState.BatchImportResult -> AppConfirmDialog(
            onDismissRequest = { WorldBookListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.world_book_batch_import_result_title),
            message = stringResource(
                R.string.batch_import_result_message,
                dialogState.successCount,
                dialogState.failureCount
            ),
            confirmText = stringResource(android.R.string.ok),
            dismissText = null,
            onConfirm = { WorldBookListUiIntent.DismissDialog.emit() },
        )

        is WorldBookListDialogState.LowTokenBudgetConfirm -> AppWarningDialog(
            onDismissRequest = { WorldBookListUiIntent.ImportWithOriginalBudget.emit() },
            title = stringResource(R.string.low_world_book_budget_title),
            message = if (dialogState.affectedBookCount == 1) {
                stringResource(
                    R.string.low_world_book_budget_message,
                    dialogState.importedTokenBudget
                )
            } else {
                stringResource(
                    R.string.world_book_batch_low_budget_message,
                    dialogState.affectedBookCount,
                    dialogState.importedTokenBudget
                )
            },
            confirmText = stringResource(R.string.follow_global_budget),
            dismissText = stringResource(R.string.keep_imported_budget),
            onConfirm = { WorldBookListUiIntent.ImportWithGlobalBudget.emit() },
        )
    }
}

@Composable
private fun LoadingRow(loadState: WorldBookListLoadState) {
    // 批量导入显示阶段计数，普通刷新和导出继续使用紧凑的加载指示。
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        if (loadState is WorldBookListLoadState.Importing) {
            Text(
                text = stringResource(
                    if (loadState.saving) R.string.batch_import_saving_progress else R.string.batch_import_reading_progress,
                    loadState.completedCount,
                    loadState.totalCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPanel() {
    AppCard(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.no_world_books),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.no_world_books_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorldBookCard(
    lorebook: WorldBookListItem,
    selectionMode: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onExport: () -> Unit,
) {
    // 多选以整卡承载交互与选中语义，尾部勾选标记替换单项编辑和导出。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (selectionMode) {
                    this.selected = selected
                    role = Role.Checkbox
                }
            }
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = if (selectionMode) null else onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = 0.28f
            )
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon = Icons.Rounded.Book, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = lorebook.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (selectionMode) {
                        BatchSelectionIndicator(selected = selected)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            IconButton(
                                onClick = onExport,
                                enabled = enabled,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = stringResource(R.string.export_world_book),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                RpTagRow(tags = listOf(stringResource(R.string.entry_count, lorebook.entryCount)))
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookListLayout(
            uiState = WorldBookListUiState.Normal(
                lorebooks = listOf(
                    WorldBookListItem(id = 1L, name = "World Setting", entryCount = 8),
                    WorldBookListItem(id = 2L, name = "Case Notes", entryCount = 3),
                ),
            ),
            emit = {},
        )
    }
}
