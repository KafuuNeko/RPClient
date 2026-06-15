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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import me.kafuuneko.rpclient.feature.groupchat.ui.GroupChatLorebookSelector
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingMode
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

/** 新建群聊页 Compose 入口，负责成员编排与世界书授权交互。 */
@Composable
fun GroupChatCreateLayout(
    uiState: GroupChatCreateUiState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit = {}
) {
    when (uiState) {
        GroupChatCreateUiState.None,
        GroupChatCreateUiState.Finished -> Unit

        is GroupChatCreateUiState.Normal -> {
            GroupChatCreateNormalView(uiState, emitIntent)
            LoadStateOverlay(uiState.loadState)
        }
    }
}

@Composable
private fun GroupChatCreateNormalView(
    state: GroupChatCreateUiState.Normal,
    emitIntent: (GroupChatCreateUiIntent) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.group_chat_create_title),
                onBack = { emitIntent(GroupChatCreateUiIntent.Back) }
            )
        },
        bottomBar = {
            CreateBottomBar(
                selectedCount = state.selectedCount,
                enabled = state.canCreate &&
                        state.loadState == GroupChatCreateLoadState.None,
                onCreate = { emitIntent(GroupChatCreateUiIntent.Create) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.group_chat_build_cast),
                    subtitle = stringResource(R.string.group_chat_build_cast_desc)
                )
            }
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = {
                        emitIntent(GroupChatCreateUiIntent.ChangeTitle(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.group_chat_title_label)) },
                    placeholder = {
                        Text(stringResource(R.string.group_chat_title_placeholder))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            item {
                StrategySection(
                    selected = state.activationStrategy,
                    onSelect = {
                        emitIntent(GroupChatCreateUiIntent.SelectStrategy(it))
                    }
                )
            }
            item {
                SelfResponseSetting(
                    enabled = state.allowSelfResponses,
                    onChange = {
                        emitIntent(
                            GroupChatCreateUiIntent.ToggleAllowSelfResponses(it)
                        )
                    }
                )
            }
            item {
                GreetingSection(
                    state = state.greetingState,
                    emitIntent = emitIntent
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.group_chat_members),
                    action = stringResource(R.string.selected_count, state.selectedCount)
                )
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = {
                        emitIntent(GroupChatCreateUiIntent.ChangeSearchQuery(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    placeholder = {
                        Text(stringResource(R.string.group_chat_search_characters))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }
            if (state.visibleCharacters.isEmpty()) {
                item { EmptyCharacterCard() }
            }
            items(
                items = state.visibleCharacters,
                key = { it.id }
            ) { character ->
                CharacterChoiceCard(
                    character = character,
                    onClick = {
                        emitIntent(
                            GroupChatCreateUiIntent.ToggleCharacter(character.id)
                        )
                    }
                )
            }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.enabled_world_book_entries)
                )
            }
            item {
                GroupChatLorebookSelector(
                    groups = state.lorebookGroups.map { group ->
                        group.copy(
                            entries = group.entries.map { entry ->
                                entry.copy(
                                    enabled =
                                        entry.id in state.selectedLorebookEntryIds
                                )
                            }
                        )
                    },
                    onToggleLorebook = {
                        emitIntent(GroupChatCreateUiIntent.ToggleLorebook(it))
                    },
                    onToggleEntry = {
                        emitIntent(GroupChatCreateUiIntent.ToggleLorebookEntry(it))
                    }
                )
            }
        }
    }
}

@Composable
private fun StrategySection(
    selected: GroupChatSession.ActivationStrategy,
    onSelect: (GroupChatSession.ActivationStrategy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_turn_strategy))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GroupChatSession.ActivationStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = selected == strategy,
                    onClick = { onSelect(strategy) },
                    label = { Text(stringResource(strategy.titleRes())) },
                    leadingIcon = if (selected == strategy) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor =
                            MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelfResponseSetting(
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stringResource(R.string.group_chat_allow_consecutive),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.group_chat_allow_consecutive_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun GreetingSection(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_greeting_mode))
        GroupChatGreetingMode.entries.forEach { mode ->
            GreetingModeOption(
                mode = mode,
                selected = state.mode == mode,
                onClick = {
                    emitIntent(GroupChatCreateUiIntent.SelectGreetingMode(mode))
                }
            )
        }
        when (state.mode) {
            GroupChatGreetingMode.RandomPerCharacter,
            GroupChatGreetingMode.None -> Unit

            GroupChatGreetingMode.Manual -> ManualGreetingEditor(
                state = state,
                emitIntent = emitIntent
            )

            GroupChatGreetingMode.Custom -> CustomGreetingEditor(
                state = state,
                emitIntent = emitIntent
            )
        }
    }
}

@Composable
private fun GreetingModeOption(
    mode: GroupChatGreetingMode,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ManualGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit
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
            style = MaterialTheme.typography.labelLarge
        )
        selectedCharacter.greetings.forEachIndexed { index, greeting ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                    },
                shape = RoundedCornerShape(14.dp),
                color = if (state.selectedGreetingIndex == index) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    RadioButton(
                        selected = state.selectedGreetingIndex == index,
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                        }
                    )
                    Text(
                        text = greeting,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit
) {
    GreetingCharacterSelector(state, emitIntent)
    if (state.selectedCharacter == null) {
        GreetingHint(R.string.group_chat_greeting_select_members_first)
        return
    }
    OutlinedTextField(
        value = state.customGreeting,
        onValueChange = {
            emitIntent(GroupChatCreateUiIntent.ChangeCustomGreeting(it))
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.group_chat_custom_greeting)) },
        placeholder = {
            Text(stringResource(R.string.group_chat_custom_greeting_placeholder))
        },
        minLines = 4,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun GreetingCharacterSelector(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit
) {
    if (state.characters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_chat_greeting_choose_character),
            style = MaterialTheme.typography.labelLarge
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.characters.forEach { character ->
                FilterChip(
                    selected = state.selectedCharacterId == character.id,
                    onClick = {
                        emitIntent(
                            GroupChatCreateUiIntent.SelectGreetingCharacter(character.id)
                        )
                    },
                    label = { Text(character.name) }
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
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun CharacterChoiceCard(
    character: GroupChatCreateCharacterItem,
    onClick: () -> Unit
) {
    val accent = getMacaronColor(character.name)
    Card(
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
            }
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (character.selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpAvatar(
                text = character.name.firstOrNull()?.uppercase() ?: "?",
                color = accent
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = character.description.ifBlank {
                        stringResource(R.string.no_description)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = if (character.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                if (character.selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCharacterCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.Groups,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.group_chat_no_matching_characters))
        }
    }
}

@Composable
private fun CreateBottomBar(
    selectedCount: Int,
    enabled: Boolean,
    onCreate: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(
                        R.plurals.group_chat_member_count,
                        selectedCount,
                        selectedCount
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        if (selectedCount >= 2) {
                            R.string.group_chat_ready_to_begin
                        } else {
                            R.string.group_chat_select_at_least_two
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onCreate,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Composable
private fun LoadStateOverlay(loadState: GroupChatCreateLoadState) {
    if (loadState == GroupChatCreateLoadState.None) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.size(14.dp))
                Text(
                    if (loadState == GroupChatCreateLoadState.Creating) {
                        stringResource(R.string.group_chat_creating)
                    } else {
                        stringResource(R.string.group_chat_loading_characters)
                    }
                )
            }
        }
    }
}

private fun GroupChatSession.ActivationStrategy.titleRes(): Int {
    return when (this) {
        GroupChatSession.ActivationStrategy.Manual -> R.string.group_chat_strategy_manual
        GroupChatSession.ActivationStrategy.Natural -> R.string.group_chat_strategy_natural
        GroupChatSession.ActivationStrategy.List -> R.string.group_chat_strategy_list
        GroupChatSession.ActivationStrategy.Pooled -> R.string.group_chat_strategy_pooled
    }
}

private fun GroupChatSession.ActivationStrategy.descriptionRes(): Int {
    return when (this) {
        GroupChatSession.ActivationStrategy.Manual ->
            R.string.group_chat_strategy_manual_desc

        GroupChatSession.ActivationStrategy.Natural ->
            R.string.group_chat_strategy_natural_desc

        GroupChatSession.ActivationStrategy.List ->
            R.string.group_chat_strategy_list_desc

        GroupChatSession.ActivationStrategy.Pooled ->
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
                    characters = previewCharacters.filter { it.selected }.map {
                        GroupChatGreetingCharacterItem(
                            id = it.id,
                            name = it.name,
                            greetings = it.greetings
                        )
                    },
                    selectedCharacterId = 1,
                    selectedGreetingIndex = 0
                )
            )
        )
    }
}

private val previewCharacters = listOf(
    GroupChatCreateCharacterItem(
        1,
        "Lyra",
        "A curious star navigator.",
        true,
        greetings = listOf("Lyra studies the unfamiliar stars.", "We made it.")
    ),
    GroupChatCreateCharacterItem(
        2,
        "Mina",
        "A calm archivist with a sharp memory.",
        true,
        greetings = listOf("Mina opens the archive.")
    ),
    GroupChatCreateCharacterItem(3, "Rowan", "An impulsive but loyal explorer.", false)
)
