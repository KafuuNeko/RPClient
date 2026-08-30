package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.utils.toggle

/** 通用会话世界书对话框中的分组展示模型。 */
data class SessionLorebookDialogGroup(
    val id: Long,
    val name: String,
    val entries: List<SessionLorebookDialogEntry>
)

/** 通用会话世界书对话框中的条目展示模型。 */
data class SessionLorebookDialogEntry(
    val id: Long,
    val name: String,
    val content: String,
    val keywords: List<String>,
    val constant: Boolean
)

/**
 * 展示当前会话的世界书条目快捷管理对话框。
 *
 * - 搜索词和勾选结果由调用方状态树维护，本组件只负责渲染并发送交互。
 * - 调用方仅在确认回调中持久化草稿，取消操作不会触发提交。
 * - 内容区域使用项目统一的 Dialog 脚手架，并限制高度以适配小屏设备。
 *
 * @param groups 当前会话全部世界书分组
 * @param visibleGroups 当前搜索条件下可见的世界书分组
 * @param query 当前搜索词
 * @param enabledEntryIds 对话框草稿中启用的条目 ID 集合
 * @param onQueryChange 搜索词变化回调
 * @param onToggleGroup 切换世界书分组回调
 * @param onToggleEntry 切换单个条目回调
 * @param onConfirmSelection 确认提交回调
 * @param onManageWorldBooks 打开全局世界书管理回调
 * @param onDismissRequest 关闭对话框回调
 */
@Composable
fun SessionLorebookDialog(
    groups: List<SessionLorebookDialogGroup>,
    visibleGroups: List<SessionLorebookDialogGroup>,
    query: String,
    enabledEntryIds: Set<Long>,
    onQueryChange: (String) -> Unit,
    onToggleGroup: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit,
    onConfirmSelection: () -> Unit,
    onManageWorldBooks: () -> Unit,
    onDismissRequest: () -> Unit
) {
    var expandedGroupIds by remember { mutableStateOf(emptySet<Long>()) }
    val listState = rememberLazyListState()
    val isSearching = query.isNotBlank()
    val completeGroupsById = remember(groups) { groups.associateBy { it.id } }

    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.session_world_books),
        subtitle = stringResource(R.string.session_lore_note),
        badgeIcon = Icons.Rounded.Book,
        compactHeader = true,
        confirmText = stringResource(R.string.confirm),
        dismissText = stringResource(R.string.cancel),
        onConfirm = onConfirmSelection,
        onDismiss = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 560.dp)
    ) {
        // 搜索框与结果列表共享固定上限，避免大量条目撑出屏幕边界。
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SessionLorebookManagerLink(onClick = onManageWorldBooks)
            if (groups.isNotEmpty()) {
                SessionLorebookSearchField(
                    query = query,
                    onQueryChange = onQueryChange
                )
            }
            when {
                groups.isEmpty() -> SessionLorebookEmptyText(
                    text = stringResource(R.string.no_world_book_entries_selectable)
                )

                visibleGroups.isEmpty() -> SessionLorebookEmptyText(
                    text = stringResource(R.string.no_world_book_search_results)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .draggableLazyListScrollIndicator(listState),
                    contentPadding = PaddingValues(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleGroups, key = { it.id }) { group ->
                        val expanded = isSearching || group.id in expandedGroupIds
                        val completeGroup = completeGroupsById.getValue(group.id)
                        val completeEntryIds = completeGroup.entries.mapTo(mutableSetOf()) { it.id }
                        SessionLorebookGroupCard(
                            group = group,
                            enabledEntryIds = enabledEntryIds,
                            enabledCount = completeEntryIds.count { it in enabledEntryIds },
                            totalCount = completeEntryIds.size,
                            allEnabled = completeEntryIds.isNotEmpty() &&
                                completeEntryIds.all { it in enabledEntryIds },
                            expanded = expanded,
                            onExpandedChange = {
                                expandedGroupIds = expandedGroupIds.toggle(group.id)
                            },
                            onToggleGroup = { onToggleGroup(group.id) },
                            onToggleEntry = onToggleEntry
                        )
                    }
                }
            }
        }
    }
}

/** 展示独立于确认操作的全局世界书管理入口。 */
@Composable
private fun SessionLorebookManagerLink(onClick: () -> Unit) {
    // 管理入口保持为内容区内的次要操作，避免与提交当前会话选择的确认按钮混淆。
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.manage_world_books),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 展示世界书搜索结果为空时的提示。 */
@Composable
private fun SessionLorebookEmptyText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 展示可折叠世界书分组及其当前会话启用状态。 */
@Composable
private fun SessionLorebookGroupCard(
    group: SessionLorebookDialogGroup,
    enabledEntryIds: Set<Long>,
    enabledCount: Int,
    totalCount: Int,
    allEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onToggleGroup: () -> Unit,
    onToggleEntry: (Long) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 分组标题负责折叠，末端开关独立控制当前会话中的整组条目。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onExpandedChange),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                RpIconBubble(Icons.Rounded.Book)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            enabledCount,
                            totalCount
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = allEnabled,
                    onCheckedChange = { onToggleGroup() }
                )
            }
            if (expanded) {
                group.entries.forEach { entry ->
                    SessionLorebookEntryRow(
                        entry = entry,
                        enabled = entry.id in enabledEntryIds,
                        onToggle = { onToggleEntry(entry.id) }
                    )
                }
            }
        }
    }
}

/** 展示单个世界书条目的摘要、标签与当前会话开关。 */
@Composable
private fun SessionLorebookEntryRow(
    entry: SessionLorebookDialogEntry,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val constantLabel = stringResource(R.string.entry_constant)
    // 整行与末端开关共享同一切换回调，扩大移动端触控区域。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RpIconBubble(Icons.Rounded.Book)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.content.isNotBlank()) {
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RpTagRow(
                tags = buildList {
                    if (entry.constant) add(constantLabel)
                    addAll(entry.keywords)
                },
                maxCount = 3
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}

/** 展示世界书名称、条目内容与关键词搜索框。 */
@Composable
private fun SessionLorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_world_books),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            // 仅在存在搜索词时提供一键清空，清空动作仍由调用方更新状态树。
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun SessionLorebookDialogPreview() {
    AppTheme(dynamicColor = false) {
        SessionLorebookDialog(
            groups = previewSessionLorebookGroups,
            visibleGroups = previewSessionLorebookGroups,
            query = "",
            enabledEntryIds = setOf(1),
            onQueryChange = {},
            onToggleGroup = {},
            onToggleEntry = {},
            onConfirmSelection = {},
            onManageWorldBooks = {},
            onDismissRequest = {}
        )
    }
}

private val previewSessionLorebookGroups = listOf(
    SessionLorebookDialogGroup(
        id = 1,
        name = "Fog Harbor",
        entries = listOf(
            SessionLorebookDialogEntry(
                id = 1,
                name = "Old District",
                content = "A rain-soaked district surrounding the abandoned archive.",
                keywords = listOf("archive", "rain"),
                constant = false
            ),
            SessionLorebookDialogEntry(
                id = 2,
                name = "Night Watch",
                content = "Patrol routes and curfew rules used after midnight.",
                keywords = listOf("night"),
                constant = true
            )
        )
    )
)
