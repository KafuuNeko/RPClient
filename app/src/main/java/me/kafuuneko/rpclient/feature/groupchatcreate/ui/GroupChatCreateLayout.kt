package me.kafuuneko.rpclient.feature.groupchatcreate.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingMode
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.CharacterSearchField
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpCreationBottomBar
import me.kafuuneko.rpclient.ui.widgets.RpCreationSwitchCard
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.groupchat.GroupChatLorebookSelector
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/** 新建群聊页 Compose 入口，负责成员编排与世界书授权交互。 */
@Composable
fun GroupChatCreateLayout(
    uiState: GroupChatCreateUiState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit = {},
) {
    when (uiState) {
        GroupChatCreateUiState.None -> Unit
        is GroupChatCreateUiState.Finished -> GroupChatCreateLayout(uiState.previous) {}

        is GroupChatCreateUiState.Normal -> {
            GroupChatCreateNormalView(uiState, emitIntent)
            LoadStateOverlay(uiState.loadState)
        }
    }
}

@Composable
private fun GroupChatCreateNormalView(
    state: GroupChatCreateUiState.Normal,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    AppPageScaffold(
        // 缩小整个表单视口，底栏与搜索框共同避让键盘；短列表也能滚动到焦点。
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.group_chat_create_title),
                onBack = { emitIntent(GroupChatCreateUiIntent.Back) },
            )
        },
        bottomBar = {
            CreateBottomBar(
                selectedCount = state.selectedCount,
                enabled = state.canCreate
                    && state.loadState == GroupChatCreateLoadState.None,
                onCreate = { emitIntent(GroupChatCreateUiIntent.Create) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = { text ->
                            emitIntent(GroupChatCreateUiIntent.ChangeTitle(text))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .generalContentBackground(
                                shape = RoundedCornerShape(16.dp),
                                hasLabel = true
                            ),
                        label = { Text(stringResource(R.string.group_chat_title_label)) },
                        placeholder = {
                            Text(stringResource(R.string.group_chat_title_placeholder))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                    )
                }
                item {
                    StrategySection(
                        selected = state.activationStrategy,
                        onSelect = { groupChatActivationStrategy ->
                            emitIntent(
                                GroupChatCreateUiIntent.SelectStrategy(
                                    groupChatActivationStrategy
                                )
                            )
                        },
                    )
                }
                item {
                    RpCreationSwitchCard(
                        title = stringResource(R.string.group_chat_allow_consecutive),
                        subtitle = stringResource(R.string.group_chat_allow_consecutive_desc),
                        icon = Icons.Rounded.AutoAwesome,
                        checked = state.allowSelfResponses,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatCreateUiIntent.ToggleAllowSelfResponses(enabled))
                        },
                    )
                }
                item {
                    GreetingSection(
                        state = state.greetingState,
                        emitIntent = emitIntent,
                    )
                }
                if (state.showCharacterExamplesSwitch) {
                    item {
                        RpCreationSwitchCard(
                            title = stringResource(R.string.chat_include_character_examples),
                            icon = Icons.Rounded.FormatQuote,
                            checked = state.includeCharacterExamples,
                            onCheckedChange = { enabled ->
                                emitIntent(
                                    GroupChatCreateUiIntent.ToggleIncludeCharacterExamples(
                                        enabled
                                    )
                                )
                            },
                        )
                    }
                }
                item {
                    RpSectionHeader(
                        title = stringResource(R.string.group_chat_members),
                        action = stringResource(R.string.selected_count, state.selectedCount),
                    )
                }
                item {
                    CharacterSearchField(
                        query = state.searchQuery,
                        onQueryChange = { text ->
                            emitIntent(
                                GroupChatCreateUiIntent.ChangeSearchQuery(
                                    text
                                )
                            )
                        },
                    )
                }
                if (state.visibleCharacters.isEmpty()) {
                    item { EmptyCharacterCard() }
                }
                items(
                    items = state.visibleCharacters,
                    key = { groupChatCreateCharacterItem -> groupChatCreateCharacterItem.id },
                ) { character ->
                    CharacterChoiceCard(
                        character = character,
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.ToggleCharacter(character.id))
                        },
                    )
                }
                item {
                    RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                }
                item {
                    GroupChatLorebookSelector(
                        groups = state.lorebookGroups,
                        visibleGroups = state.visibleLorebookGroups,
                        query = state.lorebookQuery,
                        onQueryChange = { text ->
                            emitIntent(GroupChatCreateUiIntent.ChangeLorebookQuery(text))
                        },
                        onToggleLorebook = { id ->
                            emitIntent(GroupChatCreateUiIntent.ToggleLorebook(id))
                        },
                        onToggleEntry = { id ->
                            emitIntent(GroupChatCreateUiIntent.ToggleLorebookEntry(id))
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun StrategySection(
    selected: GroupChatActivationStrategy,
    onSelect: (GroupChatActivationStrategy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_turn_strategy))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupChatActivationStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = selected == strategy,
                    onClick = { onSelect(strategy) },
                    label = {
                        Text(
                            text = stringResource(strategy.titleRes()),
                            maxLines = 1,
                        )
                    },
                    colors = pageFilterChipColors(
                        selectedContainerColor =
                            MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GreetingSection(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_greeting_mode))
        GroupChatGreetingMode.entries.forEach { mode ->
            GreetingModeOption(
                mode = mode,
                selected = state.mode == mode,
                onClick = {
                    emitIntent(GroupChatCreateUiIntent.SelectGreetingMode(mode))
                },
            )
        }
        when (state.mode) {
            GroupChatGreetingMode.RandomPerCharacter,
            GroupChatGreetingMode.None -> Unit

            GroupChatGreetingMode.Manual -> ManualGreetingEditor(
                state = state,
                emitIntent = emitIntent,
            )

            GroupChatGreetingMode.Custom -> CustomGreetingEditor(
                state = state,
                emitIntent = emitIntent,
            )
        }
    }
}

@Composable
private fun GreetingModeOption(
    mode: GroupChatGreetingMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 选中状态由单选按钮表达，底板直接使用页面统一的不透明度与模糊配置。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManualGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    GreetingCharacterSelector(state, emitIntent)
    val selectedCharacter = state.selectedCharacter
    if (selectedCharacter == null) {
        GreetingHint(R.string.group_chat_greeting_select_members_first)
        return
    }
    if (selectedCharacter.greetings.isEmpty()) {
        GreetingHint(R.string.group_chat_greeting_character_has_none)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_chat_greeting_choose_message),
            style = MaterialTheme.typography.labelLarge,
        )
        selectedCharacter.greetings.forEachIndexed { index, greeting ->
            // 手动开场白与模式选项共用底色，切换选中项不会改变底板的实际不透明度。
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                        },
                    ),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(
                        selected = state.selectedGreetingIndex == index,
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                        },
                    )
                    Text(
                        text = greeting,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    GreetingCharacterSelector(state, emitIntent)
    if (state.selectedCharacter == null) {
        GreetingHint(R.string.group_chat_greeting_select_members_first)
        return
    }
    RpScrollableOutlinedTextField(
        value = state.customGreeting,
        onValueChange = { emitIntent(GroupChatCreateUiIntent.ChangeCustomGreeting(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.group_chat_custom_greeting)) },
        placeholder = {
            Text(stringResource(R.string.group_chat_custom_greeting_placeholder))
        },
        minLines = 4,
        maxLines = 10,
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun GreetingCharacterSelector(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    if (state.characters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_chat_greeting_choose_character),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.characters.forEach { character ->
                FilterChip(
                    selected = state.selectedCharacterId == character.id,
                    onClick = {
                        emitIntent(GroupChatCreateUiIntent.SelectGreetingCharacter(character.id))
                    },
                    label = { Text(character.name) },
                )
            }
        }
    }
}

@Composable
private fun GreetingHint(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun CharacterChoiceCard(
    character: GroupChatCreateCharacterItem,
    onClick: () -> Unit,
) {
    val accent = getMacaronColor(character.name)
    // 成员选择继续由描边和勾选标记表达，底板与开场白选项保持一致。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = if (character.selected) 2.dp else 1.dp,
            color = if (character.selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            },
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            RpCheckBadge(selected = character.selected, size = 24.dp)
        }
    }
}

@Composable
private fun EmptyCharacterCard() {
    // 空状态与普通成员卡沿用同一套页面底板设置。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.group_chat_no_matching_characters))
        }
    }
}

@Composable
private fun CreateBottomBar(
    selectedCount: Int,
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    RpCreationBottomBar(
        title = pluralStringResource(
            R.plurals.group_chat_member_count,
            selectedCount,
            selectedCount
        ),
        subtitle = stringResource(
            id = if (selectedCount >= 2) R.string.group_chat_ready_to_begin
            else R.string.group_chat_select_at_least_two,
        ),
        enabled = enabled,
        onCreate = onCreate,
    )
}

@Composable
private fun LoadStateOverlay(loadState: GroupChatCreateLoadState) {
    if (loadState == GroupChatCreateLoadState.None) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AppSurface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.size(14.dp))
                Text(
                    text = if (loadState == GroupChatCreateLoadState.Creating) {
                        stringResource(R.string.group_chat_creating)
                    } else {
                        stringResource(R.string.group_chat_loading_characters)
                    },
                )
            }
        }
    }
}

private fun GroupChatActivationStrategy.titleRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual -> R.string.group_chat_strategy_manual
        GroupChatActivationStrategy.Natural -> R.string.group_chat_strategy_natural
        GroupChatActivationStrategy.List -> R.string.group_chat_strategy_list
        GroupChatActivationStrategy.Pooled -> R.string.group_chat_strategy_pooled
    }
}

private fun GroupChatActivationStrategy.descriptionRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual ->
            R.string.group_chat_strategy_manual_desc

        GroupChatActivationStrategy.Natural ->
            R.string.group_chat_strategy_natural_desc

        GroupChatActivationStrategy.List ->
            R.string.group_chat_strategy_list_desc

        GroupChatActivationStrategy.Pooled ->
            R.string.group_chat_strategy_pooled_desc
    }
}

private fun GroupChatGreetingMode.titleRes(): Int {
    return when (this) {
        GroupChatGreetingMode.RandomPerCharacter ->
            R.string.group_chat_greeting_random

        GroupChatGreetingMode.Manual ->
            R.string.group_chat_greeting_manual

        GroupChatGreetingMode.Custom ->
            R.string.group_chat_greeting_custom

        GroupChatGreetingMode.None ->
            R.string.group_chat_greeting_none
    }
}

private fun GroupChatGreetingMode.descriptionRes(): Int {
    return when (this) {
        GroupChatGreetingMode.RandomPerCharacter ->
            R.string.group_chat_greeting_random_desc

        GroupChatGreetingMode.Manual ->
            R.string.group_chat_greeting_manual_desc

        GroupChatGreetingMode.Custom ->
            R.string.group_chat_greeting_custom_desc

        GroupChatGreetingMode.None ->
            R.string.group_chat_greeting_none_desc
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun GroupChatCreatePreview() {
    ActivityPreview(darkTheme = false) {
        GroupChatCreateLayout(
            uiState = GroupChatCreateUiState.Normal(
                characters = previewCharacters,
                visibleCharacters = previewCharacters,
                greetingState = GroupChatCreateGreetingState(
                    characters = previewCharacters.filter { groupChatCreateCharacterItem -> groupChatCreateCharacterItem.selected }
                        .map { groupChatCreateCharacterItem ->
                            GroupChatGreetingCharacterItem(
                                id = groupChatCreateCharacterItem.id,
                                name = groupChatCreateCharacterItem.name,
                                greetings = groupChatCreateCharacterItem.greetings,
                            )
                        },
                    selectedCharacterId = 1,
                    selectedGreetingIndex = 0,
                ),
            ),
        )
    }
}

private val previewCharacters = listOf(
    GroupChatCreateCharacterItem(
        id = 1,
        name = "Lyra",
        description = "A curious star navigator.",
        selected = true,
        greetings = listOf("Lyra studies the unfamiliar stars.", "We made it."),
    ),
    GroupChatCreateCharacterItem(
        id = 2,
        name = "Mina",
        description = "A calm archivist with a sharp memory.",
        selected = true,
        greetings = listOf("Mina opens the archive."),
    ),
    GroupChatCreateCharacterItem(
        id = 3,
        name = "Rowan",
        description = "An impulsive but loyal explorer.",
        selected = false,
    ),
)
