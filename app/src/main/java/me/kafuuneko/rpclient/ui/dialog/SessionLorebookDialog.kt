package me.kafuuneko.rpclient.ui.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.ui.widgets.AppSurface as Surface
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 通用会话世界书对话框中的分组展示模型。 */
data class SessionLorebookDialogGroup(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前分组、请求或结果包含的条目列表。 */
    val entries: List<SessionLorebookDialogEntry>
)

/** 通用会话世界书对话框中的条目展示模型。 */
data class SessionLorebookDialogEntry(
    /** 当前记录或列表项的唯一标识。 */
    val id: Long,
    /** 供界面展示和业务识别的名称。 */
    val name: String,
    /** 当前对象承载的正文内容。 */
    val content: String,
    /** 用于触发世界书条目的主关键词列表。 */
    val keywords: List<String>,
    /** 是否忽略关键词并始终激活当前世界书条目。 */
    val constant: Boolean
)

/**
 * 展示世界书条目选择器弹窗，供单聊/群聊快捷管理、设置页添加世界书与各创建页复用。
 *
 * - 搜索词、筛选态和勾选草稿由调用方状态树维护，本组件只负责渲染并发送交互；
 * - 调用方仅在确认回调中提交选择，取消操作不会修改原配置；
 * - 分组标题与展开后的条目均以独立列表项虚拟化渲染，在世界书或条目较多时保持流畅滚动。
 *
 * @param groups 全部可选世界书分组
 * @param visibleGroups 当前搜索与筛选条件下可见的世界书分组
 * @param query 当前搜索词
 * @param enabledEntryIds 对话框草稿中启用的条目 ID 集合
 * @param onQueryChange 搜索词变化回调
 * @param onToggleGroup 切换整本世界书勾选回调
 * @param onToggleEntry 切换单个条目回调
 * @param onConfirmSelection 确认提交回调
 * @param onManageWorldBooks 可选的全局世界书管理入口回调，传入时展示快捷入口
 * @param onDismissRequest 关闭对话框回调
 * @param title 对话框标题
 * @param subtitle 对话框副标题，默认展示已启用条目计数
 * @param confirmText 底部确认按钮文案
 * @param confirmEnabled 确认按钮是否可点击
 * @param emptyText 无任何候选世界书时的空态文案
 * @param showSelectedOnly 是否仅展示已启用的世界书与条目
 * @param onShowSelectedOnlyChange 切换“全部 / 已启用”筛选回调
 */
@OptIn(ExperimentalLayoutApi::class)
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
    onManageWorldBooks: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    title: String = stringResource(R.string.session_world_books),
    subtitle: String = stringResource(R.string.world_books_enabled, enabledEntryIds.size),
    confirmText: String = stringResource(R.string.confirm),
    confirmEnabled: Boolean = true,
    emptyText: String = stringResource(R.string.no_world_book_entries_selectable),
    showSelectedOnly: Boolean = false,
    onShowSelectedOnlyChange: ((Boolean) -> Unit)? = null,
) {
    var expandedGroupIds by remember { mutableStateOf(emptySet<Long>()) }
    val listState = rememberLazyListState()
    val isSearching = query.isNotBlank()
    val completeGroupsById = remember(groups) {
        groups.associateBy { group -> group.id }
    }
    val totalEntryCount = remember(groups) {
        groups.sumOf { group -> group.entries.size }
    }

    AppDialogScaffold(
        onDismissRequest = onDismissRequest,
        title = title,
        subtitle = subtitle,
        subtitleMaxLines = 1,
        badgeIcon = Icons.AutoMirrored.Rounded.MenuBook,
        compactHeader = true,
        confirmText = confirmText,
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = confirmEnabled,
        onConfirm = onConfirmSelection,
        onDismiss = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 560.dp),
    ) {
        // 搜索框、筛选条与虚拟化列表共享固定上限，避免大量条目撑出屏幕边界
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onManageWorldBooks != null) {
                SessionLorebookManagerLink(onClick = onManageWorldBooks)
            }
            if (groups.isNotEmpty()) {
                SessionLorebookSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                )
            }
            if (onShowSelectedOnlyChange != null && groups.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = !showSelectedOnly,
                        onClick = { onShowSelectedOnlyChange(false) },
                        label = {
                            Text(
                                text = stringResource(R.string.picker_filter_all_count, totalEntryCount),
                                maxLines = 1,
                            )
                        },
                        colors = pageFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                    FilterChip(
                        selected = showSelectedOnly,
                        onClick = { onShowSelectedOnlyChange(true) },
                        label = {
                            Text(
                                text = stringResource(
                                    R.string.picker_filter_enabled_count,
                                    enabledEntryIds.size,
                                ),
                                maxLines = 1,
                            )
                        },
                        colors = pageFilterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
            // 按空库、仅看已启用和搜索无命中分别呈现空态说明
            when {
                groups.isEmpty() -> SessionLorebookEmptyText(text = emptyText)

                visibleGroups.isEmpty() && showSelectedOnly && !isSearching -> SessionLorebookEmptyText(
                    text = stringResource(R.string.picker_no_enabled_world_books),
                )

                visibleGroups.isEmpty() -> SessionLorebookEmptyText(
                    text = stringResource(R.string.no_world_book_search_results),
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .draggableLazyListScrollIndicator(listState),
                    contentPadding = PaddingValues(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 将分组标题与展开条目分别作为独立 item 渲染，确保单本大世界书也具备虚拟化性能
                    visibleGroups.forEach { group ->
                        val expanded = isSearching || group.id in expandedGroupIds
                        val completeGroup = completeGroupsById[group.id] ?: group
                        val completeEntryIds = completeGroup.entries.mapTo(mutableSetOf()) { entry ->
                            entry.id
                        }
                        val enabledCount = completeEntryIds.count { id -> id in enabledEntryIds }
                        val totalCount = completeEntryIds.size
                        val allEnabled = totalCount > 0 && enabledCount == totalCount

                        item(key = "group-${group.id}") {
                            SessionLorebookGroupHeaderCard(
                                groupName = group.name,
                                enabledCount = enabledCount,
                                totalCount = totalCount,
                                allEnabled = allEnabled,
                                expanded = expanded,
                                onExpandedChange = {
                                    expandedGroupIds = expandedGroupIds.toggle(group.id)
                                },
                                onToggleGroup = { onToggleGroup(group.id) },
                            )
                        }
                        if (expanded) {
                            items(
                                items = group.entries,
                                key = { entry -> "entry-${group.id}-${entry.id}" },
                            ) { entry ->
                                SessionLorebookEntryRow(
                                    entry = entry,
                                    enabled = entry.id in enabledEntryIds,
                                    onToggle = { onToggleEntry(entry.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 展示“添加世界书”专用检索弹窗，供单聊与群聊设置页复用。
 *
 * - 仅展示当前尚未启用的候选世界书分组，确认后由调用方将新增条目合并至会话已启用集合；
 * - 复用 [SessionLorebookDialog] 的搜索、筛选与虚拟化列表能力，未勾选任何条目时禁用确认按钮。
 *
 * @param groups 全部当前未启用的候选世界书分组
 * @param visibleGroups 经搜索与筛选后当前展示的候选世界书分组
 * @param query 当前搜索关键词
 * @param selectedEntryIds 弹窗草稿中待添加的条目 ID 集合
 * @param showSelectedOnly 是否仅展示待添加的已选条目
 * @param onQueryChange 搜索关键词变化回调
 * @param onShowSelectedOnlyChange 切换“全部 / 已启用”筛选回调
 * @param onToggleGroup 切换整本候选世界书勾选回调
 * @param onToggleEntry 切换单个候选条目回调
 * @param onConfirmSelection 确认添加回调
 * @param onManageWorldBooks 可选的全局世界书管理入口回调
 * @param onDismissRequest 关闭弹窗回调
 */
@Composable
fun AddSessionLorebooksDialog(
    groups: List<SessionLorebookDialogGroup>,
    visibleGroups: List<SessionLorebookDialogGroup>,
    query: String,
    selectedEntryIds: Set<Long>,
    showSelectedOnly: Boolean,
    onQueryChange: (String) -> Unit,
    onShowSelectedOnlyChange: (Boolean) -> Unit,
    onToggleGroup: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit,
    onConfirmSelection: () -> Unit,
    onManageWorldBooks: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    // 未选择任何新增条目时禁止提交，避免空操作触发不必要的刷新
    val selectedCount = selectedEntryIds.size
    val confirmText = if (selectedCount > 0) {
        stringResource(R.string.picker_add_confirm, selectedCount)
    } else {
        stringResource(R.string.confirm)
    }
    SessionLorebookDialog(
        groups = groups,
        visibleGroups = visibleGroups,
        query = query,
        enabledEntryIds = selectedEntryIds,
        title = stringResource(R.string.world_book_add_title),
        subtitle = stringResource(R.string.selected_count, selectedCount),
        confirmText = confirmText,
        confirmEnabled = selectedCount > 0,
        emptyText = stringResource(R.string.world_book_all_enabled),
        showSelectedOnly = showSelectedOnly,
        onQueryChange = onQueryChange,
        onShowSelectedOnlyChange = onShowSelectedOnlyChange,
        onToggleGroup = onToggleGroup,
        onToggleEntry = onToggleEntry,
        onConfirmSelection = onConfirmSelection,
        onManageWorldBooks = onManageWorldBooks,
        onDismissRequest = onDismissRequest,
    )
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
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.manage_world_books),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 展示可折叠世界书分组标题卡片及其当前会话启用统计。 */
@Composable
private fun SessionLorebookGroupHeaderCard(
    groupName: String,
    enabledCount: Int,
    totalCount: Int,
    allEnabled: Boolean,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onToggleGroup: () -> Unit,
) {
    val hasEnabledEntries = enabledCount > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpandedChange),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (hasEnabledEntries) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
        ),
        color = if (hasEnabledEntries) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        },
    ) {
        // 分组标题负责折叠，末端复选徽章独立控制整本世界书条目
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                size = 32.dp,
                iconSize = 17.dp,
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.enabled_entries_count,
                        enabledCount,
                        totalCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            val indeterminate = enabledCount > 0 && !allEnabled
            RpCheckBadge(
                selected = allEnabled,
                indeterminate = indeterminate,
                onClick = onToggleGroup,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowDown
                } else {
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 展示缩进的单个世界书条目卡片、关键词摘要与启用勾选。 */
@Composable
private fun SessionLorebookEntryRow(
    entry: SessionLorebookDialogEntry,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val constantLabel = stringResource(R.string.entry_constant)
    // 通过左侧缩进呈现分组归属，整卡点击切换启用状态以保证移动端触控面积
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (enabled) 1.5.dp else 1.dp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
            },
        ),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.content.isNotBlank()) {
                    Text(
                        text = entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RpTagRow(
                    tags = buildList {
                        if (entry.constant) add(constantLabel)
                        addAll(entry.keywords)
                    },
                    maxCount = 3,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            RpCheckBadge(selected = enabled)
        }
    }
}

/** 展示世界书名称、条目内容与关键词搜索框。 */
@Composable
private fun SessionLorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
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
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            // 仅在存在搜索词时提供一键清空，清空动作仍由调用方更新状态树。
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
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
            onShowSelectedOnlyChange = {},
            onDismissRequest = {},
        )
    }
}

private val previewSessionLorebookGroups = listOf(
    element = SessionLorebookDialogGroup(
        id = 1,
        name = "Fog Harbor",
        entries = listOf(
            SessionLorebookDialogEntry(
                id = 1,
                name = "Old District",
                content = "A rain-soaked district surrounding the abandoned archive.",
                keywords = listOf("archive", "rain"),
                constant = false,
            ),
            SessionLorebookDialogEntry(
                id = 2,
                name = "Night Watch",
                content = "Patrol routes and curfew rules used after midnight.",
                keywords = listOf("night"),
                constant = true,
            ),
        ),
    ),
)
