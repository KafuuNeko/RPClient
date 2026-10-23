package me.kafuuneko.rpclient.feature.storyeditor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterActivationMode
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryCharacterOptionItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookEntryItem
import me.kafuuneko.rpclient.feature.storyeditor.model.StoryLorebookGroupItem
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorPageState
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StoryEditorUiIntent
import me.kafuuneko.rpclient.feature.storyeditor.presentation.StorySettingsSection
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.DraggableItem
import me.kafuuneko.rpclient.ui.widgets.PageButton
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.StoryUserPersonaCard
import me.kafuuneko.rpclient.ui.widgets.dragContainer
import me.kafuuneko.rpclient.ui.widgets.draggableScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.rememberLazyListDragDropState
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch

/**
 * 渲染故事设置页，分区和设置草稿继续由页面状态驱动。
 *
 * @param state 当前故事设置状态
 * @param emit 原有故事编辑意图发送入口
 */
@Composable
internal fun StorySettingsPage(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 设置分区和保存状态继续由原有页面状态驱动。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() },
                actions = {
                    TextButton(
                        onClick = { StoryEditorUiIntent.SaveStorySettings.emit() },
                        enabled = !state.isSaving
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SettingsSectionTabs(state.selectedSection, emit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (state.selectedSection) {
                    StorySettingsSection.Context -> ContextSettings(state, emit)
                    StorySettingsSection.Characters -> CharacterSettings(state, emit)
                    StorySettingsSection.Lorebook -> LorebookSettings(state, emit)
                }
            }
        }
    }
}

/**
 * 渲染故事设置加载页，保留加载期间的返回操作。
 *
 * @param emit 原有故事编辑意图发送入口
 */
@Composable
internal fun StorySettingsLoadingPage(emit: StoryEditorUiIntent.() -> Unit) {
    // 加载期间保留设置页返回入口，继续沿用关闭设置意图。
    AppPageScaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_settings),
                onBack = { StoryEditorUiIntent.CloseStorySettings.emit() }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.story_loading_settings))
            }
        }
    }
}

@Composable
private fun SettingsSectionTabs(
    selected: StorySettingsSection,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 分区选择回传原有意图，横向滚动只负责容纳标签。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab(
            selected = selected == StorySettingsSection.Context,
            label = stringResource(R.string.story_context_settings)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Context).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Characters,
            label = stringResource(R.string.story_character_references)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Characters).emit() }
        SettingsTab(
            selected = selected == StorySettingsSection.Lorebook,
            label = stringResource(R.string.world_book)
        ) { StoryEditorUiIntent.SelectSettingsSection(StorySettingsSection.Lorebook).emit() }
    }
}

@Composable
private fun SettingsTab(selected: Boolean, label: String, onClick: () -> Unit) {
    // 选中标记只由外部状态控制，不在标签内部保存选择。
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            null
        }
    )
}

@Composable
private fun ContextSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 保存期间沿用原有字段禁用规则，滚动状态仍由当前设置分区持有。
    val controlsEnabled = !state.isSaving
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .draggableScrollIndicator(scrollState)
            .verticalScroll(scrollState)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        UserPersonaSetting(state, controlsEnabled, emit)
        SettingIntro(
            icon = Icons.AutoMirrored.Rounded.MenuBook,
            title = stringResource(R.string.story_memory),
            subtitle = stringResource(R.string.story_memory_desc)
        )
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.memory,
            onValueChange = { StoryEditorUiIntent.ChangeMemory(it).emit() },
            label = { Text(stringResource(R.string.story_memory)) },
            minLines = 6,
            maxLines = 12,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        SettingIntro(
            icon = Icons.Rounded.AutoAwesome,
            title = stringResource(R.string.story_summary),
            subtitle = stringResource(R.string.story_summary_desc)
        )
        StorySummarySettings(state, emit)
        SettingIntro(
            icon = Icons.Rounded.Edit,
            title = stringResource(R.string.story_author_note),
            subtitle = stringResource(R.string.story_author_note_desc)
        )
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.authorNote,
            onValueChange = { StoryEditorUiIntent.ChangeAuthorNote(it).emit() },
            label = { Text(stringResource(R.string.story_author_note)) },
            minLines = 4,
            maxLines = 10,
            enabled = controlsEnabled,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UserPersonaSetting(
    state: StoryEditorPageState.Settings,
    enabled: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    StoryUserPersonaCard(
        checked = state.includeUserPersona,
        onCheckedChange = {
            StoryEditorUiIntent.SetIncludeUserPersona(it).emit()
        },
        enabled = enabled
    )
}

@Composable
private fun StorySummarySettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 摘要字段继续使用原有草稿和修改意图。
    RpScrollableOutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.summary,
        onValueChange = { StoryEditorUiIntent.ChangeSummary(it).emit() },
        label = { Text(stringResource(R.string.story_summary)) },
        minLines = 6,
        maxLines = 12,
        enabled = !state.isSaving,
        shape = RoundedCornerShape(16.dp)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        PageButton(
            onClick = { StoryEditorUiIntent.SummarizeStory.emit() },
            enabled = !state.isSaving
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.story_summarize_now))
        }
    }
}

@Composable
private fun CharacterSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    if (state.characters.isEmpty()) {
        EmptySettingsMessage(R.string.story_no_characters)
        return
    }
    val selectedCount = state.characters.count { it.selected }
    // 未选角色不属于当前故事引用，保留原有名称排序且不参与拖动
    val listState = rememberLazyListState()
    val draggableCharacterIds = remember(state.characters, state.isSaving) {
        if (state.isSaving) {
            emptySet()
        } else {
            state.characters.filter { it.selected }.map { it.id }.toSet()
        }
    }
    val dragDropState = rememberLazyListDragDropState(
        lazyListState = listState,
        isItemDraggable = { key -> key in draggableCharacterIds },
        onMove = { fromKey, toKey ->
            val selected = state.characters.filter { it.selected }
            val fromIndex = selected.indexOfFirst { it.id == fromKey }
            val toIndex = selected.indexOfFirst { it.id == toKey }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                StoryEditorUiIntent.MoveStoryCharacter(
                    characterId = selected[fromIndex].id,
                    offset = toIndex - fromIndex
                ).emit()
            }
        }
    )
    RpLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .dragContainer(dragDropState),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_character_references_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.characters, key = { it.id }) { character ->
            val selectedIndex = state.characters
                .filter { it.selected }
                .indexOfFirst { it.id == character.id }
            DraggableItem(dragDropState, character.id) {
                CharacterSettingCard(
                    character = character,
                    canMoveUp = character.selected && selectedIndex > 0,
                    canMoveDown = character.selected && selectedIndex in 0 until selectedCount - 1,
                    emit = emit
                )
            }
        }
    }
}

@Composable
private fun CharacterSettingCard(
    character: StoryCharacterOptionItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 角色选择、排序与编辑操作沿用原有状态和意图。
    AppCard(
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (character.selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (character.selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpIconBubble(Icons.Rounded.Group)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = character.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = character.selected,
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleStoryCharacter(character.id).emit()
                    }
                )
            }
            if (character.selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Primary,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Primary
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_primary)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Always,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Always
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_always)) }
                    )
                    FilterChip(
                        selected = character.activationMode == StoryCharacterActivationMode.Auto,
                        onClick = {
                            StoryEditorUiIntent.SetCharacterActivationMode(
                                character.id,
                                StoryCharacterActivationMode.Auto
                            ).emit()
                        },
                        label = { Text(stringResource(R.string.story_character_auto)) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, -1).emit()
                        },
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            contentDescription = stringResource(R.string.move_up)
                        )
                    }
                    IconButton(
                        onClick = {
                            StoryEditorUiIntent.MoveStoryCharacter(character.id, 1).emit()
                        },
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Rounded.ArrowDownward,
                            contentDescription = stringResource(R.string.move_down)
                        )
                    }
                }
                character.linkedLorebookName?.let { lorebookName ->
                    RpTagRow(
                        tags = listOf(
                            stringResource(R.string.story_linked_lorebook, lorebookName)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookSettings(
    state: StoryEditorPageState.Settings,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 世界书分组继续使用设置草稿，不在视图内重建业务数据。
    if (state.lorebookGroups.isEmpty()) {
        EmptySettingsMessage(R.string.no_world_books)
        return
    }
    RpLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.story_lorebook_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(state.lorebookGroups, key = { it.id }) { group ->
            LorebookGroupCard(
                group = group,
                emit = emit
            )
        }
    }
}

@Composable
private fun LorebookGroupCard(
    group: StoryLorebookGroupItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 分组展开和条目选择沿用原有状态归属及修改意图。
    var expanded by remember(group.id) { mutableStateOf(false) }
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (group.selectedCount > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            }
        ),
        color = if (group.selectedCount > 0) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
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
                    Text(group.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            group.selectedCount,
                            group.entries.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = group.isAllSelected,
                    enabled = group.entries.isNotEmpty(),
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleLorebook(group.id).emit()
                    }
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (group.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.story_lorebook_empty),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        group.entries.forEach { entry ->
                            LorebookEntryRow(entry, emit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryRow(
    entry: StoryLorebookEntryItem,
    emit: StoryEditorUiIntent.() -> Unit
) {
    // 条目展示和启用操作沿用原有设置草稿。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit() },
        shape = RoundedCornerShape(14.dp),
        color = if (entry.selected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    val constantLabel = if (entry.constant) {
                        stringResource(R.string.entry_constant)
                    } else {
                        null
                    }
                    val tags = listOfNotNull(constantLabel) + entry.keywords
                    if (tags.isNotEmpty()) {
                        RpTagRow(tags = tags, maxCount = 3)
                    } else {
                        Text(
                            text = entry.contentPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = entry.selected,
                    onCheckedChange = {
                        StoryEditorUiIntent.ToggleLorebookEntry(entry.id).emit()
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingIntro(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    // 说明区沿用相同图标和文案层级，保持各设置分区的视觉结构。
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(icon)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySettingsMessage(messageRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(messageRes),
            modifier = Modifier.padding(28.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
