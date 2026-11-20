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
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateDialogState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateLoadState
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiIntent
import me.kafuuneko.rpclient.feature.storycreate.presentation.StoryCreateUiState
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialog
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialogItem
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerMode
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCreationBottomBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.UserPersonaPicker
import me.kafuuneko.rpclient.ui.widgets.WorldBookSelectionSummaryCard
import me.kafuuneko.rpclient.ui.widgets.WorldBookSelectionSummaryItem
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground

/** 新建 Story 页面 Compose 入口。 */
@Composable
fun StoryCreateLayout(
    uiState: StoryCreateUiState,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    BackHandler(
        enabled = uiState is StoryCreateUiState.Normal,
        onBack = { StoryCreateUiIntent.Back.emit() },
    )
    when (uiState) {
        StoryCreateUiState.None -> Unit
        is StoryCreateUiState.Normal -> {
            StoryCreateNormal(state = uiState, emit = emit)
            DialogSwitch(state = uiState, emit = emit)
        }

        is StoryCreateUiState.Finished -> StoryCreateLayout(uiState = uiState.previous, emit = {})
    }
}

@Composable
private fun StoryCreateNormal(
    state: StoryCreateUiState.Normal,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    val controlsEnabled = state.loadState == StoryCreateLoadState.Ready
    val selectedCharacters = remember(state.characters, state.form.selectedCharacterIds) {
        state.characters.filter { character -> character.id in state.form.selectedCharacterIds }
    }
    val selectedWorldBookSummaries = remember(
        state.lorebookGroups,
        state.form.selectedLorebookEntryIds,
    ) {
        state.lorebookGroups.mapNotNull { group ->
            val count = group.entries.count { entry ->
                entry.id in state.form.selectedLorebookEntryIds
            }
            if (count > 0) {
                WorldBookSelectionSummaryItem(
                    lorebookId = group.lorebookId,
                    name = group.lorebookName,
                    selectedCount = count,
                    totalCount = group.entryCount,
                )
            } else {
                null
            }
        }
    }

    AppPageScaffold(
        // 缩小整个表单视口，底栏与输入区共同避让键盘
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
                    // 故事标题与默认人设
                    item {
                        StoryTitleField(
                            form = state.form,
                            enabled = controlsEnabled,
                            emit = emit,
                        )
                    }
                    item {
                        UserPersonaOption(
                            form = state.form,
                            enabled = controlsEnabled,
                            emit = emit,
                        )
                    }
                    // 角色参考区：主表单仅呈现已选角色及其激活模式，避免海量未选角色阻塞页面
                    item {
                        RpSectionHeader(
                            title = stringResource(R.string.story_character_references),
                            action = if (state.characters.isNotEmpty() && controlsEnabled) {
                                stringResource(
                                    if (selectedCharacters.isNotEmpty()) R.string.manage
                                    else R.string.picker_choose,
                                )
                            } else {
                                null
                            },
                            onAction = { StoryCreateUiIntent.ShowCharacterPicker.emit() },
                        )
                    }
                    when {
                        state.characters.isEmpty() -> {
                            item {
                                EmptyCard(
                                    icon = Icons.Rounded.Person,
                                    text = stringResource(R.string.story_no_characters),
                                )
                            }
                        }

                        selectedCharacters.isEmpty() -> {
                            item {
                                StoryCharactersPromptCard(
                                    totalCount = state.characters.size,
                                    enabled = controlsEnabled,
                                    onClick = { StoryCreateUiIntent.ShowCharacterPicker.emit() },
                                )
                            }
                        }

                        else -> {
                            items(
                                items = selectedCharacters,
                                key = { character -> "selected-character-${character.id}" },
                            ) { character ->
                                SelectedStoryCharacterCard(
                                    character = character,
                                    activationMode = state.form.activationModeOf(character.id),
                                    enabled = controlsEnabled,
                                    onRemove = {
                                        StoryCreateUiIntent.ToggleCharacter(character.id).emit()
                                    },
                                    onActivationModeClick = { activationMode ->
                                        StoryCreateUiIntent.SetCharacterActivationMode(
                                            characterId = character.id,
                                            activationMode = activationMode,
                                        ).emit()
                                    },
                                )
                            }
                        }
                    }
                    // 世界书摘要区：展示活跃书目概览并拉起独立检索选择器
                    item {
                        RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                    }
                    item {
                        WorldBookSelectionSummaryCard(
                            totalGroupCount = state.lorebookGroups.size,
                            selectedEntryCount = state.form.selectedLorebookEntryIds.size,
                            selectedGroups = selectedWorldBookSummaries,
                            enabled = controlsEnabled,
                            onClick = { StoryCreateUiIntent.ShowLorebookPicker.emit() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DialogSwitch(
    state: StoryCreateUiState.Normal,
    emit: StoryCreateUiIntent.() -> Unit,
) {
    // 按互斥弹窗状态分发角色参考多选器与世界书选择器
    when (val dialogState = state.dialogState) {
        StoryCreateDialogState.None -> Unit

        is StoryCreateDialogState.CharacterPicker -> {
            val visibleItems = remember(dialogState.visibleCharacters) {
                dialogState.visibleCharacters.map { character ->
                    CharacterPickerDialogItem(
                        id = character.id,
                        name = character.name,
                        description = character.description,
                        tags = character.tags,
                        avatarImage = character.avatarImage,
                    )
                }
            }
            CharacterPickerDialog(
                title = stringResource(R.string.picker_select_characters_title),
                mode = CharacterPickerMode.Multiple,
                totalCount = state.characters.size,
                visibleCharacters = visibleItems,
                selectedIds = dialogState.selectedCharacterIds,
                query = dialogState.query,
                showSelectedOnly = dialogState.showSelectedOnly,
                emptyText = stringResource(R.string.story_no_characters),
                onQueryChange = { text -> StoryCreateUiIntent.ChangeCharacterQuery(text).emit() },
                onShowSelectedOnlyChange = { selectedOnly ->
                    StoryCreateUiIntent.ToggleCharacterPickerSelectedOnly(selectedOnly).emit()
                },
                onVisibleCharactersChanged = { visibleIds, targetSizePx ->
                    StoryCreateUiIntent.VisiblePickerCharactersChanged(
                        characterIds = visibleIds,
                        targetSizePx = targetSizePx,
                    ).emit()
                },
                onCharacterClick = { id ->
                    StoryCreateUiIntent.ToggleCharacterPickerItem(id).emit()
                },
                onConfirm = { StoryCreateUiIntent.ConfirmCharacterPicker.emit() },
                onDismissRequest = { StoryCreateUiIntent.DismissDialog.emit() },
            )
        }

        is StoryCreateDialogState.LorebookPicker -> {
            val allDialogGroups = remember(state.lorebookGroups) {
                state.lorebookGroups.map(StoryCreateLorebookGroupItem::toSessionLorebookDialogGroup)
            }
            val visibleDialogGroups = remember(dialogState.visibleGroups) {
                dialogState.visibleGroups.map(
                    StoryCreateLorebookGroupItem::toSessionLorebookDialogGroup,
                )
            }
            SessionLorebookDialog(
                groups = allDialogGroups,
                visibleGroups = visibleDialogGroups,
                query = dialogState.query,
                enabledEntryIds = dialogState.enabledEntryIds,
                title = stringResource(R.string.enabled_world_book_entries),
                showSelectedOnly = dialogState.showSelectedOnly,
                onQueryChange = { text -> StoryCreateUiIntent.ChangeLorebookQuery(text).emit() },
                onShowSelectedOnlyChange = { selectedOnly ->
                    StoryCreateUiIntent.ToggleLorebookPickerSelectedOnly(selectedOnly).emit()
                },
                onToggleGroup = { id -> StoryCreateUiIntent.ToggleLorebook(id).emit() },
                onToggleEntry = { id -> StoryCreateUiIntent.ToggleLorebookEntry(id).emit() },
                onConfirmSelection = { StoryCreateUiIntent.ConfirmLorebookPicker.emit() },
                onDismissRequest = { StoryCreateUiIntent.DismissDialog.emit() },
            )
        }
    }
}

/** 将新建 Story 页的世界书分组转换为通用选择器弹窗展示模型。 */
private fun StoryCreateLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
    return SessionLorebookDialogGroup(
        id = lorebookId,
        name = lorebookName,
        entries = entries.map { entry ->
            SessionLorebookDialogEntry(
                id = entry.id,
                name = entry.name,
                content = entry.content,
                keywords = entry.keywords,
                constant = entry.constant,
            )
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

/** 未选择任何角色参考时的引导卡片，点击打开角色多选检索弹窗。 */
@Composable
private fun StoryCharactersPromptCard(
    totalCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 故事允许零角色开篇，未选时提供紧凑入口引导按需检索添加
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon = Icons.Rounded.Person)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.story_characters_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.picker_character_tap_to_choose, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 展示已选参演角色卡及其主角 / 常驻 / 自动激活模式配置，支持直接移除。 */
@Composable
private fun SelectedStoryCharacterCard(
    character: StoryCreateCharacterItem,
    activationMode: StoryCreateCharacterActivationMode,
    enabled: Boolean,
    onRemove: () -> Unit,
    onActivationModeClick: (StoryCreateCharacterActivationMode) -> Unit,
) {
    val accent = getMacaronColor(character.name)
    // 主表单只展示已加入阵容的角色，直接暴露激活模式切换与一键移除
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpAvatar(
                    text = character.name.firstOrNull()?.uppercase() ?: "?",
                    color = accent,
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    image = character.avatarImage,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
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
                IconButton(
                    enabled = enabled,
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.picker_remove_selection),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
            character.linkedLorebookName?.let { lorebookName ->
                RpTagRow(
                    tags = listOf(stringResource(R.string.story_linked_lorebook, lorebookName)),
                )
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
    UserPersonaPicker(
        state = form.persona,
        allowNone = true,
        onSelect = { StoryCreateUiIntent.SelectPersona(it).emit() },
        enabled = enabled,
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
                    characterActivationModes = mapOf(1L to StoryCreateCharacterActivationMode.Primary),
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
            ),
            emit = {},
        )
    }
}
