package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.utils.toggle
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 群聊创建页和设置页共用的世界书分组选择器。 */
@Composable
fun GroupChatLorebookSelector(
    groups: List<GroupChatLorebookGroupItem>,
    onToggleLorebook: (Long) -> Unit,
    onToggleEntry: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val filteredGroups = groups.filterForQuery(query)
    val isSearching = query.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (groups.isNotEmpty()) {
            LorebookSearchField(
                query = query,
                onQueryChange = { query = it }
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
            filteredGroups.isEmpty() -> {
                Text(
                    text = stringResource(R.string.no_world_book_search_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> filteredGroups.forEach { group ->
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
                Spacer(Modifier.width(4.dp))
                RpIconBubble(Icons.Rounded.Book)
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = group.isAllEnabled,
                    onCheckedChange = { onToggleLorebook(group.lorebookId) }
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

/** 展示世界书条目摘要与当前会话启用开关。 */
@Composable
private fun LorebookEntryRow(
    entry: GroupChatLorebookEntryItem,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RpIconBubble(Icons.Rounded.Book)
        Spacer(Modifier.width(10.dp))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Switch(
            checked = entry.enabled,
            onCheckedChange = { onToggle() }
        )
    }
}

/** 世界书名称和条目内容的搜索输入框。 */
@Composable
private fun LorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        label = { Text(stringResource(R.string.search_world_books)) },
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
private fun List<GroupChatLorebookGroupItem>.filterForQuery(
    query: String
): List<GroupChatLorebookGroupItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    return mapNotNull { group ->
        val groupMatches =
            group.lorebookName.contains(normalizedQuery, ignoreCase = true)
        val matchingEntries = group.entries.filter {
            it.matchesQuery(normalizedQuery)
        }
        when {
            groupMatches -> group
            matchingEntries.isNotEmpty() -> group.copy(entries = matchingEntries)
            else -> null
        }
    }
}

/** 判断条目是否命中世界书搜索词。 */
private fun GroupChatLorebookEntryItem.matchesQuery(query: String): Boolean {
    return lorebookName.contains(query, ignoreCase = true) ||
        name.contains(query, ignoreCase = true) ||
        content.contains(query, ignoreCase = true) ||
        keywords.any { it.contains(query, ignoreCase = true) } ||
        secondaryKeywords.any { it.contains(query, ignoreCase = true) }
}
