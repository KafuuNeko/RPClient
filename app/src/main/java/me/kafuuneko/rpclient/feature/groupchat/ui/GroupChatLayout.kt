package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatLoadState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.room.entity.GroupChatMessage
import me.kafuuneko.rpclient.libs.room.entity.GroupChatSession
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.message.MarkdownMessageText
import me.kafuuneko.rpclient.ui.message.MessageContentPart
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import androidx.compose.ui.res.stringResource

/** 群聊页 Compose 入口，根据状态渲染对话、成员与世界书设置。 */
@Composable
fun GroupChatLayout(
    uiState: GroupChatUiState,
    emitIntent: (GroupChatUiIntent) -> Unit = {}
) {
    BackHandler(enabled = uiState is GroupChatUiState.Normal) {
        emitIntent(GroupChatUiIntent.Back)
    }
    when (uiState) {
        GroupChatUiState.None,
        GroupChatUiState.Finished -> Unit
        is GroupChatUiState.Normal -> {
            GroupChatNormalView(uiState, emitIntent)
            DialogSwitch(uiState.dialogState, emitIntent)
            LoadStateOverlay(uiState.loadState)
        }
    }
}

@Composable
private fun GroupChatNormalView(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    if (state.page == GroupChatPage.Settings) {
        GroupChatSettingsView(state, emitIntent)
        return
    }
    val generating = state.generationState is GroupChatGenerationState.Generating
    val canContinue = state.messages.any {
        it.source == GroupChatMessage.Source.Character
    }
    Scaffold(
        topBar = {
            AppTopBar(
                title = state.title,
                onBack = { emitIntent(GroupChatUiIntent.Back) },
                actions = {
                    IconButton(
                        onClick = { emitIntent(GroupChatUiIntent.OpenPromptInspector) },
                        enabled = state.hasPromptInspection
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.prompt_inspector_title)
                        )
                    }
                    IconButton(
                        onClick = { emitIntent(GroupChatUiIntent.OpenSettings) },
                        enabled = !generating
                    ) {
                        Icon(
                            Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(
                        onClick = {
                            emitIntent(GroupChatUiIntent.DeleteSessionClick)
                        },
                        enabled = !generating
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(
                                R.string.group_chat_delete_content_description
                            )
                        )
                    }
                }
            )
        },
        bottomBar = {
            Composer(
                draft = state.inputDraft,
                generating = generating,
                onDraftChange = {
                    emitIntent(GroupChatUiIntent.ChangeInputDraft(it))
                },
                onSend = { emitIntent(GroupChatUiIntent.SendMessage) },
                onStop = { emitIntent(GroupChatUiIntent.StopGeneration) },
                canContinue = canContinue,
                onContinue = { emitIntent(GroupChatUiIntent.ContinueLast) },
                onSummarize = { emitIntent(GroupChatUiIntent.SummarizeNow) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GroupHeader(
                strategy = state.activationStrategy,
                generationState = state.generationState,
                canContinue = canContinue,
                onContinue = { emitIntent(GroupChatUiIntent.ContinueLast) }
            )
            MemberRail(
                members = state.members,
                selectedSpeakerId = state.selectedSpeakerId,
                enabled = !generating,
                onSelect = {
                    emitIntent(GroupChatUiIntent.SelectSpeaker(it))
                },
                onToggleMuted = {
                    emitIntent(GroupChatUiIntent.ToggleMemberMuted(it))
                }
            )
            MessageList(
                messages = state.messages,
                expandedThinkBlockIds = state.expandedThinkBlockIds,
                editingMessageId = state.editingMessageId,
                editingMessageDraft = state.editingMessageDraft,
                emitIntent = emitIntent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChatSettingsView(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.group_chat_settings),
            onBack = { emitIntent(GroupChatUiIntent.CloseSettings) },
            actions = {
                TextButton(onClick = { emitIntent(GroupChatUiIntent.SaveSettings) }) {
                    Text(stringResource(R.string.save))
                }
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_basic_settings)
                ) {
                    OutlinedTextField(
                        value = state.titleDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeTitle(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_title_label)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = state.scenarioDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeScenario(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_scenario)) },
                        minLines = 3,
                        maxLines = 8
                    )
                    OutlinedTextField(
                        value = state.userNoteDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeUserNote(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_author_note)) },
                        minLines = 3,
                        maxLines = 8
                    )
                    OutlinedTextField(
                        value = state.summaryDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeSummary(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.summary_memory)) },
                        minLines = 4,
                        maxLines = 10
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.summarize_now),
                        subtitle = stringResource(R.string.summarize_now_desc),
                        onClick = { emitIntent(GroupChatUiIntent.SummarizeNow) }
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restore_previous_summary),
                        subtitle = stringResource(R.string.restore_previous_summary_desc),
                        onClick = {
                            emitIntent(GroupChatUiIntent.RestorePreviousSummary)
                        }
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.pause_auto_summary),
                        subtitle = stringResource(R.string.pause_auto_summary_desc),
                        checked = state.autoSummaryPaused,
                        onCheckedChange = {
                            emitIntent(GroupChatUiIntent.ToggleAutoSummaryPaused(it))
                        }
                    )
                }
            }
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_turn_strategy)
                ) {
                    Text(
                        text = stringResource(R.string.group_chat_turn_strategy),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroupChatSession.ActivationStrategy.entries.forEach { strategy ->
                            FilterChip(
                                selected = state.activationStrategy == strategy,
                                onClick = {
                                    emitIntent(
                                        GroupChatUiIntent.SelectActivationStrategy(strategy)
                                    )
                                },
                                label = { Text(stringResource(strategy.titleRes())) }
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.group_chat_character_cards),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GroupChatSession.CharacterCardMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.characterCardMode == mode,
                                onClick = {
                                    emitIntent(GroupChatUiIntent.SelectCharacterCardMode(mode))
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            if (mode == GroupChatSession.CharacterCardMode.Swap) {
                                                R.string.group_chat_card_mode_swap
                                            } else {
                                                R.string.group_chat_card_mode_join
                                            }
                                        )
                                    )
                                }
                            )
                        }
                    }
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_include_muted_cards),
                        checked = state.includeMutedCards,
                        onCheckedChange = {
                            emitIntent(GroupChatUiIntent.ToggleIncludeMutedCards(it))
                        }
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_allow_consecutive),
                        checked = state.allowSelfResponses,
                        onCheckedChange = {
                            emitIntent(GroupChatUiIntent.ToggleAllowSelfResponses(it))
                        }
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_auto_mode),
                        checked = state.autoModeEnabled,
                        onCheckedChange = {
                            emitIntent(GroupChatUiIntent.ToggleAutoMode(it))
                        }
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_trim_other_speakers),
                        checked = state.trimOtherSpeakers,
                        onCheckedChange = {
                            emitIntent(GroupChatUiIntent.ToggleTrimOtherSpeakers(it))
                        }
                    )
                }
            }
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_prompt_overrides)
                ) {
                    Text(
                        text = stringResource(R.string.group_chat_prompt_overrides_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.systemPromptDraft,
                        onValueChange = {
                            emitIntent(GroupChatUiIntent.ChangeSystemPrompt(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_main_title)) },
                        minLines = 3,
                        maxLines = 10
                    )
                    OutlinedTextField(
                        value = state.groupNudgePromptDraft,
                        onValueChange = {
                            emitIntent(GroupChatUiIntent.ChangeGroupNudgePrompt(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_group_nudge_title)) },
                        minLines = 3,
                        maxLines = 10
                    )
                    OutlinedTextField(
                        value = state.newGroupChatPromptDraft,
                        onValueChange = {
                            emitIntent(GroupChatUiIntent.ChangeNewGroupChatPrompt(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.prompt_new_group_chat_title))
                        },
                        minLines = 2,
                        maxLines = 8
                    )
                }
            }
            item {
                GroupSettingsSection(title = stringResource(R.string.group_chat_members)) {
                    state.members.forEachIndexed { index, member ->
                        GroupMemberSettingsRow(
                            member = member,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.members.lastIndex,
                            canRemove = state.members.size > 2,
                            onMoveUp = {
                                emitIntent(GroupChatUiIntent.MoveMember(member.id, -1))
                            },
                            onMoveDown = {
                                emitIntent(GroupChatUiIntent.MoveMember(member.id, 1))
                            },
                            onRemove = {
                                emitIntent(GroupChatUiIntent.RemoveMember(member.id))
                            }
                        )
                    }
                    state.availableCharacters
                        .filterNot { it.alreadyMember }
                        .forEach { character ->
                            SettingsActionRow(
                                icon = Icons.Rounded.Groups,
                                title = stringResource(
                                    R.string.group_chat_add_member,
                                    character.name
                                ),
                                onClick = {
                                    emitIntent(GroupChatUiIntent.AddMember(character.id))
                                }
                            )
                        }
                }
            }
            item {
                GroupSettingsSection(title = stringResource(R.string.world_book)) {
                    Text(
                        text = stringResource(R.string.session_lore_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GroupChatLorebookSelector(
                        groups = state.lorebookGroups,
                        onToggleLorebook = {
                            emitIntent(GroupChatUiIntent.ToggleLorebook(it))
                        },
                        onToggleEntry = {
                            emitIntent(GroupChatUiIntent.ToggleLorebookEntry(it))
                        }
                    )
                }
            }
            item {
                Button(
                    onClick = { emitIntent(GroupChatUiIntent.SaveSettings) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/** 使用与单聊设置页一致的卡片式分区容器。 */
@Composable
private fun GroupSettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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

/** 展示带图标、标题和可选说明的设置操作项。 */
@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 展示群成员及其排序、移除操作。 */
@Composable
private fun GroupMemberSettingsRow(
    member: GroupChatMemberItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpAvatar(
                text = member.name.take(1),
                color = getMacaronColor(member.name),
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = member.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = null)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Rounded.ArrowDownward, contentDescription = null)
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = if (canRemove) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun GroupHeader(
    strategy: GroupChatSession.ActivationStrategy,
    generationState: GroupChatGenerationState,
    canContinue: Boolean,
    onContinue: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.group_chat_turn_order,
                        stringResource(strategy.titleRes())
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (generationState) {
                        GroupChatGenerationState.Idle ->
                            stringResource(R.string.group_chat_cast_ready)
                        is GroupChatGenerationState.Generating ->
                            stringResource(
                                R.string.group_chat_speaker_replying,
                                generationState.speakerName,
                                generationState.current,
                                generationState.total
                            )
                        is GroupChatGenerationState.Failed -> generationState.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (generationState is GroupChatGenerationState.Generating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (canContinue) {
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.continue_latest_reply))
                }
            }
        }
    }
}

@Composable
private fun MemberRail(
    members: List<GroupChatMemberItem>,
    selectedSpeakerId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onToggleMuted: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        members.forEach { member ->
            MemberChip(
                member = member,
                selected = selectedSpeakerId == member.id,
                enabled = enabled,
                onSelect = { onSelect(member.id) },
                onToggleMuted = { onToggleMuted(member.id) }
            )
        }
    }
}

@Composable
private fun MemberChip(
    member: GroupChatMemberItem,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleMuted: () -> Unit
) {
    val accent = getMacaronColor(member.name)
    Card(
        modifier = Modifier
            .width(138.dp)
            .alpha(if (member.muted) 0.55f else 1f)
            .clickable(enabled = enabled, onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpAvatar(
                text = member.name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                modifier = Modifier.size(38.dp)
            )
            Text(
                text = member.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onToggleMuted,
                enabled = enabled,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (member.muted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (member.muted) R.string.unmute else R.string.mute
                    ),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<GroupChatMessageItem>,
    expandedThinkBlockIds: Set<String>,
    editingMessageId: Long?,
    editingMessageDraft: String,
    emitIntent: (GroupChatUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var isFirstLoad by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScrollForward ->
                if (!canScrollForward) {
                    shouldFollowBottom = true
                }
            }
    }
    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            snapshotFlow { listState.canScrollForward }
                .collect { canScrollForward ->
                    shouldFollowBottom = !canScrollForward
                }
        }
    }
    LaunchedEffect(
        messages.size,
        messages.lastOrNull()?.content,
        expandedThinkBlockIds
    ) {
        if (messages.isNotEmpty() && (isFirstLoad || shouldFollowBottom)) {
            listState.scrollToItem(messages.size)
            isFirstLoad = false
        }
    }
    if (messages.isEmpty()) {
        EmptyConversation(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            count = messages.size,
            key = { messages[it].id }
        ) { index ->
            val message = messages[index]
            MessageBubble(
                message = message,
                editing = editingMessageId == message.id,
                editingDraft = editingMessageDraft
                    .takeIf { editingMessageId == message.id }
                    .orEmpty(),
                expandedThinkBlockIds = expandedThinkBlockIds,
                onToggleThinkBlock = { blockId ->
                    emitIntent(GroupChatUiIntent.ToggleThinkBlock(blockId))
                },
                emitIntent = emitIntent
            )
        }
        item(key = "conversation-end") {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = stringResource(R.string.group_chat_room_ready),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.group_chat_room_ready_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: GroupChatMessageItem,
    editing: Boolean,
    editingDraft: String,
    expandedThinkBlockIds: Set<String>,
    onToggleThinkBlock: (String) -> Unit,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    val isUser = message.source == GroupChatMessage.Source.User
    val isSystem = message.source == GroupChatMessage.Source.System
    val accent = getMacaronColor(message.speakerName)
    var showActions by remember(message.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isSystem -> Arrangement.Center
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser && !isSystem) {
            RpAvatar(
                text = message.speakerName.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
        }
        Column(
            modifier = Modifier.fillMaxWidth(if (isSystem) 0.92f else if (isUser) 0.82f else 0.88f),
            horizontalAlignment = when {
                isUser -> Alignment.End
                isSystem -> Alignment.CenterHorizontally
                else -> Alignment.Start
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.speakerName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else if (isSystem) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        accent
                    }
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = message.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            Surface(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .clickable(enabled = !editing) {
                        showActions = !showActions
                    },
                shape = when {
                    isUser -> RoundedCornerShape(20.dp, 5.dp, 20.dp, 20.dp)
                    isSystem -> RoundedCornerShape(14.dp)
                    else -> RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp)
                },
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = if (!isUser && !isSystem) {
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                } else {
                    null
                }
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editing) {
                        GroupMessageEditContent(
                            draft = editingDraft,
                            isUser = isUser,
                            emitIntent = emitIntent
                        )
                    } else {
                        GroupMessageContent(
                            message = message,
                            isUser = isUser,
                            accent = accent,
                            expandedThinkBlockIds = expandedThinkBlockIds,
                            onToggleThinkBlock = onToggleThinkBlock
                        )
                    }
                    AnimatedVisibility(
                        visible = showActions && !editing && !message.isStreaming,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        GroupMessageActions(
                            message = message,
                            isUser = isUser,
                            emitIntent = emitIntent
                        )
                    }
                }
            }
        }
    }
}

/** 在群聊消息气泡内展示编辑输入框。 */
@Composable
private fun GroupMessageContent(
    message: GroupChatMessageItem,
    isUser: Boolean,
    accent: Color,
    expandedThinkBlockIds: Set<String>,
    onToggleThinkBlock: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.isStreaming && (message.content.isBlank() || message.parts.isEmpty())) {
            GroupStreamingStatus(
                text = stringResource(R.string.waiting_for_response),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
                indicatorColor = accent
            )
        }
        message.parts.forEach { part ->
            when (part) {
                is MessageContentPart.Text -> {
                    if (part.content.isNotBlank()) {
                        MarkdownMessageText(
                            content = part.content,
                            isUser = isUser
                        )
                    }
                }
                is MessageContentPart.Think -> GroupThinkBlock(
                    part = part,
                    expanded = part.id in expandedThinkBlockIds,
                    isThinking = message.isStreaming && !part.isComplete,
                    indicatorColor = accent,
                    onToggle = { onToggleThinkBlock(part.id) }
                )
            }
        }
    }
}

@Composable
private fun GroupStreamingStatus(
    text: String,
    color: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = indicatorColor
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun GroupThinkBlock(
    part: MessageContentPart.Think,
    expanded: Boolean,
    isThinking: Boolean,
    indicatorColor: Color,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isThinking) {
                    GroupStreamingStatus(
                        text = stringResource(R.string.thinking),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        indicatorColor = indicatorColor,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.thought_process),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = stringResource(if (expanded) R.string.hide else R.string.show),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Text(
                    text = part.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun GroupMessageEditContent(
    draft: String,
    isUser: Boolean,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = {
                emitIntent(GroupChatUiIntent.ChangeEditingMessageDraft(it))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                unfocusedTextColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                cursorColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                focusedBorderColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.42f)
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { emitIntent(GroupChatUiIntent.SaveEditingMessage) }) {
                Text(
                    text = stringResource(R.string.save),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            TextButton(
                onClick = { emitIntent(GroupChatUiIntent.CancelEditingMessage) }
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

/** 展示群聊消息的内联操作栏。 */
@Composable
private fun GroupMessageActions(
    message: GroupChatMessageItem,
    isUser: Boolean,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    val iconColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    val actionModifier = @Composable { onClick: () -> Unit ->
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(6.dp)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                modifier = actionModifier {
                    emitIntent(GroupChatUiIntent.CopyMessage(message.id))
                },
                tint = iconColor
            )
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = actionModifier {
                    emitIntent(GroupChatUiIntent.StartEditMessage(message.id))
                },
                tint = iconColor
            )
            if (message.source == GroupChatMessage.Source.Character) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.regenerate),
                    modifier = actionModifier {
                        emitIntent(GroupChatUiIntent.RegenerateMessage(message.id))
                    },
                    tint = iconColor
                )
            }
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.delete),
                modifier = actionModifier {
                    emitIntent(GroupChatUiIntent.DeleteMessage(message.id))
                },
                tint = iconColor
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    generating: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onSummarize: () -> Unit
) {
    var quickActionsExpanded by remember { mutableStateOf(false) }
    Surface(
        tonalElevation = 5.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp, max = 140.dp),
                enabled = !generating,
                placeholder = { Text(stringResource(R.string.group_chat_message_hint)) },
                shape = RoundedCornerShape(18.dp),
                maxLines = 5,
                leadingIcon = {
                    Box {
                        IconButton(
                            onClick = { quickActionsExpanded = true },
                            enabled = !generating
                        ) {
                            Icon(
                                Icons.Rounded.AutoAwesome,
                                contentDescription = stringResource(R.string.chat_settings_actions)
                            )
                        }
                        DropdownMenu(
                            expanded = quickActionsExpanded,
                            onDismissRequest = { quickActionsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.continue_latest_reply)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                },
                                enabled = canContinue,
                                onClick = {
                                    quickActionsExpanded = false
                                    onContinue()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.summarize_now)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                },
                                onClick = {
                                    quickActionsExpanded = false
                                    onSummarize()
                                }
                            )
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = if (generating) onStop else onSend,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = if (generating) {
                        Icons.Rounded.StopCircle
                    } else {
                        Icons.AutoMirrored.Rounded.Send
                    },
                    contentDescription = stringResource(
                        if (generating) R.string.stop else R.string.send
                    )
                )
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: GroupChatDialogState,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    when (dialogState) {
        GroupChatDialogState.None -> Unit
        is GroupChatDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) }
        )
        is GroupChatDialogState.DeleteSessionConfirm -> AlertDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.group_chat_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.group_chat_delete_message,
                        dialogState.title
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        emitIntent(GroupChatUiIntent.ConfirmDeleteSession)
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { emitIntent(GroupChatUiIntent.DismissDialog) }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun LoadStateOverlay(loadState: GroupChatLoadState) {
    if (loadState == GroupChatLoadState.None) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    when (loadState) {
                        GroupChatLoadState.Deleting -> stringResource(R.string.deleting)
                        GroupChatLoadState.Saving -> stringResource(R.string.saving)
                        GroupChatLoadState.Summarizing ->
                            stringResource(R.string.updating_summary)
                        else -> stringResource(R.string.loading)
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

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun GroupChatPreview() {
    ActivityPreview(darkTheme = false) {
        GroupChatLayout(
            uiState = GroupChatUiState.Normal(
                sessionId = 1,
                title = "Starlight Crew",
                activationStrategy = GroupChatSession.ActivationStrategy.Natural,
                members = previewMembers,
                messages = previewMessages,
                selectedSpeakerId = 1
            )
        )
    }
}

private val previewMembers = listOf(
    GroupChatMemberItem(1, "Lyra", "", false),
    GroupChatMemberItem(2, "Mina", "", false),
    GroupChatMemberItem(3, "Rowan", "", true)
)

private val previewMessages = listOf(
    GroupChatMessageItem(
        id = 1,
        source = GroupChatMessage.Source.User,
        speakerName = "You",
        content = "The signal is coming from the abandoned station.",
        time = "21:04"
    ),
    GroupChatMessageItem(
        id = 2,
        source = GroupChatMessage.Source.Character,
        speakerName = "Lyra",
        content = "Then we should approach quietly. Its navigation lights are still active.",
        time = "21:04"
    ),
    GroupChatMessageItem(
        id = 3,
        source = GroupChatMessage.Source.Character,
        speakerName = "Mina",
        content = "I will check the archive for its last registered crew.",
        time = "21:05"
    )
)
