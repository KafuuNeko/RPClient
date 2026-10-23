package me.kafuuneko.rpclient.ui.widgets.groupchat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
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
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.utils.toggle

/** 群聊创建页和设置页共用的世界书分组选择器。 */
@Composable
fun GroupChatLorebookSelector(
    groups: List<GroupChatLorebookGroupItem>,
    visibleGroups: List<GroupChatLorebookGroupItem>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleLorebook: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit
) {
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSearching = query.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (groups.isNotEmpty()) {
            LorebookSearchField(
                query = query,
                onQueryChange = onQueryChange
            )
        }
        when {
            groups.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_world_book_entries_selectable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            visibleGroups.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_world_book_search_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> visibleGroups.forEach { group ->
                val expanded = isSearching || group.lorebookId in expandedLorebookIds
                LorebookGroup(
                    group = group,
                    expanded = expanded,
                    onExpandedChange = {
                        expandedLorebookIds =
                            expandedLorebookIds.toggle(group.lorebookId)
                    },
                    onToggleLorebook = onToggleLorebook,
                    onToggleEntry = onToggleEntry
                )
            }
        }
    }
}

/** 展示一个可折叠的世界书分组及其会话启用数量。 */
@Composable
private fun LorebookGroup(
    group: GroupChatLorebookGroupItem,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    onToggleLorebook: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!expanded) Modifier.clickable(onClick = onExpandedChange) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (expanded) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .then(if (expanded) Modifier.clickable(onClick = onExpandedChange) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RpIconBubble(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    size = 32.dp,
                    iconSize = 17.dp,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.lorebookName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            group.enabledCount,
                            group.totalCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                val allSelected = group.isAllEnabled
                val indeterminate = group.enabledCount > 0 && !group.isAllEnabled
                RpCheckBadge(
                    selected = allSelected,
                    indeterminate = indeterminate,
                    onClick = { onToggleLorebook(group.lorebookId) }
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            if (expanded) {
                group.entries.forEach { entry ->
                    LorebookEntryRow(
                        entry = entry,
                        onToggle = { onToggleEntry(entry.id) }
                    )
                }
            }
        }
    }
}

/** 使用与创建对话页一致的卡片和复选框展示世界书条目。 */
@Composable
private fun LorebookEntryRow(
    entry: GroupChatLorebookEntryItem,
    onToggle: () -> Unit
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (entry.enabled) 1.5.dp else 1.dp,
            color = if (entry.enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            }
        ),
        color = if (entry.enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(
                    tags = buildList {
                        if (entry.constant) add(stringResource(R.string.entry_constant))
                        addAll(entry.keywords)
                    },
                    maxCount = 3
                )
            }
            Spacer(Modifier.width(12.dp))
            RpCheckBadge(selected = entry.enabled)
        }
    }
}

/** 世界书名称和条目内容的搜索输入框。 */
@Composable
private fun LorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .generalContentBackground(RoundedCornerShape(12.dp)),
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

/** 按世界书名称、条目名称、内容和关键词过滤分组。 */

/** 判断条目是否命中世界书搜索词。 */
