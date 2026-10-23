package me.kafuuneko.rpclient.feature.storycreate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterActivationMode
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateCharacterItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateForm
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.storycreate.model.StoryCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.CharacterSearchField
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpCreationBottomBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.StoryUserPersonaCard
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 新建 Story 页面 Compose 入口。 */
@Composable
fun StoryCreateLayout(
    uiState: StoryCreateUiState,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    BackHandler(
        enabled = uiState is StoryCreateUiState.Normal,
        onBack = {
            StoryCreateUiIntent.Back.emit()
        },
    )
    when (uiState) {
        StoryCreateUiState.None -> Unit
        is StoryCreateUiState.Normal -> StoryCreateNormal(uiState, emit)
        is StoryCreateUiState.Finished -> StoryCreateLayout(uiState = uiState.previous, emit = {})
    }
}

@Composable
private fun StoryCreateNormal(
    state: StoryCreateUiState.Normal,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val searchingLorebooks = state.lorebookQuery.isNotBlank()
    val controlsEnabled = state.loadState == StoryCreateLoadState.Ready

    AppPageScaffold(
        // 缩小整个表单视口，底栏与搜索框共同避让键盘；短列表也能滚动到焦点。
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.story_create_story),
                onBack = { StoryCreateUiIntent.Back.emit() },
            )
        },
        bottomBar = {
            CreateBottomBar(
                loadState = state.loadState,
                selectedCharacterCount = state.selectedCharacterCount,
                selectedEntryCount = state.form.selectedLorebookEntryIds.size,
                emit = emit,
            )
        },
        content = { padding ->
            RpLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 14.dp,
                    end = 18.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.loadState == StoryCreateLoadState.Loading) {
                    item { LoadingRow() }
                } else {
                    item { StoryTitleField(form = state.form, enabled = controlsEnabled, emit = emit) }
                    item { UserPersonaOption(form = state.form, enabled = controlsEnabled, emit = emit) }
                    item {
                        RpSectionHeader(
                            title = stringResource(R.string.story_character_references),
                            action = stringResource(
                                R.string.selected_count,
                                state.selectedCharacterCount,
                            ),
                        )
                    }
                    if (state.characters.isNotEmpty()) {
                        item {
                            CharacterSearchField(
                                query = state.characterQuery,
                                enabled = controlsEnabled,
                                onQueryChange = { text ->
                                    StoryCreateUiIntent.ChangeCharacterQuery(text).emit()
                                },
                            )
                        }
                    }
                    if (state.characters.isEmpty()) {
                        item {
                            EmptyCard(
                                icon = Icons.Rounded.Person,
                                text = stringResource(R.string.story_no_characters),
                            )
                        }
                    } else if (state.visibleCharacters.isEmpty()) {
                        item {
                            EmptyCharacterSearchCard()
                        }
                    }
                    items(
                        items = state.visibleCharacters,
                        key = { storyCreateCharacterItem -> "character-${storyCreateCharacterItem.id}" },
                    ) { character ->
                        CharacterOption(
                            character = character,
                            selected = character.id in state.form.selectedCharacterIds,
                            activationMode = state.form.activationModeOf(character.id),
                            enabled = controlsEnabled,
                            onClick = { StoryCreateUiIntent.ToggleCharacter(character.id).emit() },
                            onActivationModeClick = { activationMode ->
                                StoryCreateUiIntent.SetCharacterActivationMode(
                                    characterId = character.id,
                                    activationMode = activationMode,
                                ).emit()
                            },
                        )
                    }
                    item {
                        RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                    }
                    item {
                        Text(
                            text = stringResource(R.string.story_lorebook_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.lorebookGroups.isNotEmpty()) {
                        item {
                            LorebookSearchField(
                                query = state.lorebookQuery,
                                enabled = controlsEnabled,
                                onQueryChange = { text ->
                                    StoryCreateUiIntent.ChangeLorebookQuery(text).emit()
                                },
                            )
                        }
                    }
                    if (state.lorebookGroups.isEmpty()) {
                        item {
                            EmptyCard(
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                text = stringResource(R.string.no_world_book_entries_selectable),
                            )
                        }
                    } else if (state.visibleLorebookGroups.isEmpty()) {
                        item {
                            EmptyCard(
                                icon = Icons.Rounded.Search,
                                text = stringResource(R.string.no_world_book_search_results),
                            )
                        }
                    }
                    items(
                        items = state.visibleLorebookGroups,
                        key = { storyCreateLorebookGroupItem -> "lorebook-${storyCreateLorebookGroupItem.lorebookId}" },
                    ) { group ->
                        val selectedCount = state.lorebookGroups
                            .firstOrNull { storyCreateLorebookGroupItem -> storyCreateLorebookGroupItem.lorebookId == group.lorebookId }
                            ?.entries
                            ?.count { storyCreateLorebookEntryItem -> storyCreateLorebookEntryItem.id in state.form.selectedLorebookEntryIds }
                            ?: 0
                        LorebookGroupOption(
                            group = group,
                            selectedEntryIds = state.form.selectedLorebookEntryIds,
                            selectedCount = selectedCount,
                            expanded = searchingLorebooks || group.lorebookId in expandedLorebookIds,
                            enabled = controlsEnabled,
                            onExpandedChange = {
                                expandedLorebookIds = expandedLorebookIds.toggle(group.lorebookId)
                            },
                            emit = emit,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun StoryTitleField(
    form: StoryCreateForm,
    enabled: Boolean,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .generalContentBackground(shape = RoundedCornerShape(12.dp), hasLabel = true),
        value = form.title,
        onValueChange = { StoryCreateUiIntent.ChangeTitle(it).emit() },
        label = { Text(stringResource(R.string.story_title)) },
        leadingIcon = { Icon(imageVector = Icons.Rounded.AutoStories, contentDescription = null) },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun CharacterOption(
    character: StoryCreateCharacterItem,
    selected: Boolean,
    activationMode: StoryCreateCharacterActivationMode,
    enabled: Boolean,
    onClick: () -> Unit,
    onActivationModeClick: (StoryCreateCharacterActivationMode) -> Unit,
) {
    val accent = getMacaronColor(character.name)
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            RpAvatar(
                text = character.name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = character.description.ifBlank {
                        stringResource(R.string.no_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RpCheckBadge(selected = selected, size = 24.dp)
            }
            if (selected) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = activationMode == StoryCreateCharacterActivationMode.Primary,
                        onClick = {
                            onActivationModeClick(StoryCreateCharacterActivationMode.Primary)
                        },
                        enabled = enabled,
                        label = { Text(stringResource(R.string.story_character_primary)) },
                    )
                    FilterChip(
                        selected = activationMode == StoryCreateCharacterActivationMode.Always,
                        onClick = {
                            onActivationModeClick(StoryCreateCharacterActivationMode.Always)
                        },
                        enabled = enabled,
                        label = { Text(stringResource(R.string.story_character_always)) },
                    )
                    FilterChip(
                        selected = activationMode == StoryCreateCharacterActivationMode.Auto,
                        onClick = {
                            onActivationModeClick(StoryCreateCharacterActivationMode.Auto)
                        },
                        enabled = enabled,
                        label = { Text(stringResource(R.string.story_character_auto)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserPersonaOption(
    form: StoryCreateForm,
    enabled: Boolean,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    StoryUserPersonaCard(
        checked = form.includeUserPersona,
        onCheckedChange = { StoryCreateUiIntent.SetIncludeUserPersona(it).emit() },
        enabled = enabled,
    )
}

@Composable
private fun EmptyCharacterSearchCard() {
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.no_matching_characters))
        }
    }
}

@Composable
private fun LorebookGroupOption(
    group: StoryCreateLorebookGroupItem,
    selectedEntryIds: Set<Long>,
    selectedCount: Int,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: () -> Unit,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!expanded) Modifier.clickable(enabled = enabled, onClick = onExpandedChange) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = if (expanded) 14.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .then(if (expanded) Modifier.clickable(enabled = enabled, onClick = onExpandedChange) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RpIconBubble(
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    size = 32.dp,
                    iconSize = 17.dp,
                    shape = RoundedCornerShape(10.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.lorebookName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.enabled_entries_count,
                            selectedCount,
                            group.entryCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                val allSelected = group.entryCount > 0 && selectedCount == group.entryCount
                val indeterminate = selectedCount > 0 && selectedCount < group.entryCount
                RpCheckBadge(
                    selected = allSelected,
                    indeterminate = indeterminate,
                    onClick = if (enabled) {
                        { StoryCreateUiIntent.ToggleLorebook(group.lorebookId).emit() }
                    } else null,
                )
                Spacer(Modifier.width(2.dp))
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
            if (expanded) {
                group.entries.forEach { entry ->
                    LorebookEntryOption(
                        entry = entry,
                        selected = entry.id in selectedEntryIds,
                        enabled = enabled,
                        onClick = {
                            StoryCreateUiIntent.ToggleLorebookEntry(entry.id).emit()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LorebookEntryOption(
    entry: StoryCreateLorebookEntryItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val constantLabel = stringResource(R.string.entry_constant)
    val orderDepthLabel = stringResource(
        R.string.entry_order_depth,
        entry.order,
        entry.depth,
    )
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                RpTagRow(
                    tags = buildList {
                        add(entry.lorebookName)
                        if (entry.constant) add(constantLabel)
                        add(orderDepthLabel)
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            RpCheckBadge(selected = selected)
        }
    }
}

@Composable
private fun LorebookSearchField(
    query: String,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
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
            )
        },
        leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    enabled = enabled,
                    onClick = { onQueryChange("") },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun CreateBottomBar(
    loadState: StoryCreateLoadState,
    selectedCharacterCount: Int,
    selectedEntryCount: Int,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    RpCreationBottomBar(
        title = stringResource(R.string.creation_selected_characters, selectedCharacterCount),
        subtitle = stringResource(R.string.world_books_enabled, selectedEntryCount),
        enabled = loadState == StoryCreateLoadState.Ready,
        creating = loadState == StoryCreateLoadState.Creating,
        onCreate = { StoryCreateUiIntent.CreateStory.emit() },
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(
    icon: ImageVector,
    text: String,
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun StoryCreateLayoutPreview() {
    AppTheme(dynamicColor = false) {
        val lorebookGroup = StoryCreateLorebookGroupItem(
            lorebookId = 2L,
            lorebookName = "Fog Harbor",
            entries = listOf(
                element = StoryCreateLorebookEntryItem(
                    id = 10L,
                    lorebookName = "Fog Harbor",
                    name = "Old Town",
                    content = "A rain-soaked district surrounding the central archive.",
                    keywords = listOf("old town"),
                    constant = false,
                    order = 100,
                    depth = 0,
                ),
            ),
        )
        StoryCreateLayout(
            uiState = StoryCreateUiState.Normal(
                loadState = StoryCreateLoadState.Ready,
                form = StoryCreateForm(
                    title = "Rain over the old city",
                    characterActivationModes = mapOf(1L to StoryCreateCharacterActivationMode.Auto),
                    selectedLorebookEntryIds = setOf(10L),
                ),
                characters = listOf(
                    element = StoryCreateCharacterItem(
                        id = 1L,
                        name = "Lyra",
                        description = "An archivist following a trail through the old city.",
                        tags = listOf("Mystery"),
                        linkedLorebookId = 2L,
                        linkedLorebookName = "Fog Harbor",
                    ),
                ),
                lorebookGroups = listOf(lorebookGroup),
                visibleLorebookGroups = listOf(lorebookGroup),
            ),
            emit = {},
        )
    }
}
