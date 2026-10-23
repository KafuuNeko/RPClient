package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch

/**
 * 渲染单聊设置页，字段草稿与自动保存时序沿用原实现。
 *
 * @param session 当前会话配置
 * @param lorebookState 会话世界书状态
 * @param loadState 当前加载状态
 * @param emit 原有单聊意图发送入口
 */
@Composable
internal fun ChatSettingsPage(
    session: ChatSessionItem,
    lorebookState: ChatLorebookState,
    loadState: ChatLoadState,
    emit: ChatUiIntent.() -> Unit
) {
    // 沿用会话和世界书状态节点，各设置项继续发送原有意图。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor())
    ) {
        AppTopBar(
            title = stringResource(R.string.chat_settings),
            onBack = { ChatUiIntent.CloseChatSettings.emit() }
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.chat_settings_actions)) {
                    MenuAction(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.regenerate_latest_reply),
                        subtitle = stringResource(R.string.regenerate_latest_reply_desc)
                    ) { ChatUiIntent.RegenerateLast.emit() }
                    MenuAction(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.continue_latest_reply),
                        subtitle = stringResource(R.string.continue_latest_reply_desc)
                    ) { ChatUiIntent.ContinueLast.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Edit,
                        title = stringResource(R.string.impersonate_user),
                        subtitle = stringResource(R.string.impersonate_user_desc)
                    ) { ChatUiIntent.ImpersonateUser.emit() }
                    MenuAction(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.summarize_now),
                        subtitle = stringResource(R.string.summarize_now_desc)
                    ) { ChatUiIntent.SummarizeNow.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restore_previous_summary),
                        subtitle = stringResource(R.string.restore_previous_summary_desc)
                    ) { ChatUiIntent.RestorePreviousSummary.emit() }
                    MenuAction(
                        icon = Icons.Rounded.FileUpload,
                        title = stringResource(R.string.export_chat),
                        subtitle = stringResource(R.string.export_chat_desc),
                        enabled = loadState == ChatLoadState.None
                    ) { ChatUiIntent.ExportChatClick.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.delete_chat_title),
                        subtitle = stringResource(R.string.delete_chat_desc),
                        iconTint = MaterialTheme.colorScheme.error,
                        enabled = loadState == ChatLoadState.None
                    ) { ChatUiIntent.DeleteSessionClick.emit() }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    SummaryPauseRow(
                        paused = session.autoSummaryPaused,
                        onPausedChange = {
                            ChatUiIntent.ToggleAutoSummaryPaused(it).emit()
                        }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.title),
                        value = session.title,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { ChatUiIntent.SaveTitle(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.current_summary),
                        value = session.summarize,
                        placeholder = stringResource(R.string.no_summary_yet),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveSummary(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_note),
                        value = session.userNote,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveUserNote(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_display_name),
                        value = session.userName,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { ChatUiIntent.SaveUserName(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_persona_description),
                        value = session.userDescription,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveUserDescription(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.creator_notes),
                        value = session.creatorNotes,
                        placeholder = stringResource(R.string.using_character_default_or_empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveCreatorNotes(it).emit() }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.world_book)) {
                    MenuAction(
                        icon = Icons.AutoMirrored.Rounded.MenuBook,
                        title = stringResource(R.string.world_book_manager),
                        subtitle = stringResource(R.string.world_book_subtitle)
                    ) { ChatUiIntent.OpenWorldBookManager.emit() }
                    SessionLoreSettings(
                        groups = lorebookState.groups,
                        visibleGroups = lorebookState.visibleGroups,
                        query = lorebookState.query,
                        emit = emit
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionLoreGroup(
    group: ChatLorebookGroupItem,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    emit: ChatUiIntent.() -> Unit
) {
    // 分组标题独立控制展开，条目操作继续通过原有意图回传。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 44.dp)
                .clickable { onExpandedChange() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                size = 32.dp,
                iconSize = 17.dp,
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.lorebookName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.enabled_entries_count,
                        group.enabledCount,
                        group.totalCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = group.isAllEnabled,
                onCheckedChange = { ChatUiIntent.ToggleSessionLorebook(group.lorebookId).emit() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
        if (expanded) {
            group.entries.forEach { entry ->
                SessionLoreEntryRow(entry, emit)
            }
        }
    }
}

@Composable
private fun SessionLoreEntryRow(
    entry: ChatLorebookEntryItem,
    emit: ChatUiIntent.() -> Unit
) {
    // 条目启用状态来自会话配置，开关继续发送条目切换意图。
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                entry.lorebookName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
            )
            RpTagRow(
                tags = entry.displayTags(stringResource(R.string.entry_constant)),
                maxCount = 2
            )
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = { ChatUiIntent.ToggleSessionLoreEntry(entry.id).emit() }
        )
    }
}

private fun ChatLorebookEntryItem.displayTags(constantLabel: String): List<String> {
    return buildList {
        if (constant) add(constantLabel)
        addAll(keywords)
    }
}

@Composable
private fun LorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    // 查询内容由页面状态持有，清空按钮沿用同一修改回调。
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().generalContentBackground(RoundedCornerShape(12.dp)),
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

@Composable
private fun SummaryPauseRow(
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit
) {
    // 暂停状态由会话配置提供，开关只回传用户操作。
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pause_auto_summary),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.pause_auto_summary_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }
        Switch(checked = paused, onCheckedChange = onPausedChange)
    }
}

@Composable
private fun SessionLoreSettings(
    groups: List<ChatLorebookGroupItem>,
    visibleGroups: List<ChatLorebookGroupItem>,
    query: String,
    emit: ChatUiIntent.() -> Unit
) {
    // 搜索期间自动展开匹配分组，手动展开集合仍保留在当前组合中。
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSearching = query.isNotBlank()

    if (groups.isEmpty()) {
        Text(
            text = stringResource(R.string.no_world_book_entries),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    LorebookSearchField(
        query = query,
        onQueryChange = { ChatUiIntent.ChangeLorebookQuery(it).emit() }
    )
    if (visibleGroups.isEmpty()) {
        Text(
            text = stringResource(R.string.no_world_book_search_results),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    visibleGroups.forEach { group ->
        val expanded = isSearching || group.lorebookId in expandedLorebookIds
        SessionLoreGroup(
            group = group,
            expanded = expanded,
            onExpandedChange = {
                expandedLorebookIds = expandedLorebookIds.toggle(group.lorebookId)
            },
            emit = emit
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    // 分区沿用统一表面和内容间距，避免拆分文件改变设置页外观。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RpSectionHeader(title = title)
            content()
        }
    }
}

@Composable
private fun AutoSaveTextField(
    label: String,
    value: String,
    placeholder: String? = null,
    minLines: Int,
    maxLines: Int,
    singleLine: Boolean = false,
    onSave: (String) -> Unit
) {
    // 编辑草稿和焦点保留在字段内部，外部值同步与防抖保存沿用原有时序。
    var text by remember(label) { mutableStateOf(value) }
    var isFocused by remember(label) { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused && text != value) {
            text = value
        }
    }

    LaunchedEffect(text, value) {
        if (text != value) {
            delay(450)
            onSave(text)
        }
    }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { placeholderText ->
        { Text(placeholderText) }
    }

    RpScrollableOutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = placeholderContent,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // 整行点击遵循同一可用状态，并同步呈现禁用颜色。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    )
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.58f else 0.38f)
                    )
                }
            }
        }
    }
}
