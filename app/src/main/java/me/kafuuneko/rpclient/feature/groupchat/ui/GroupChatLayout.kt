package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatConversationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatLoadState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.groupchat.GroupChatLorebookSelector

/** 当前窗口顶部进入该范围时预取更早消息。 */
private const val HISTORY_LOAD_THRESHOLD = 4

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
        GroupChatUiState.None -> Unit
        is GroupChatUiState.Finished -> GroupChatLayout(uiState.previous) {}
        is GroupChatUiState.Normal -> {
            GroupChatNormalView(uiState, emitIntent)
            DialogSwitch(uiState, emitIntent)
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
        GroupChatSettingsView(state.settingsState, state.members, emitIntent)
        return
    }
    val generating = state.conversationState.generationState is GroupChatGenerationState.Generating
    val canContinue = state.conversationState.messages.any {
        it.source == GroupChatMessageSource.Character
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
                        onClick = {
                            emitIntent(GroupChatUiIntent.ShowSessionLoreDialog)
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Book,
                            contentDescription = stringResource(R.string.session_world_books),
                            tint = if (
                                state.dialogState is GroupChatDialogState.SessionLorebook
                            ) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
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
                }
            )
        },
        bottomBar = {
            Composer(
                draft = state.conversationState.inputDraft,
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
            if (!state.hasAvailableProvider) {
                NoProviderBanner(
                    onClick = { emitIntent(GroupChatUiIntent.OpenProviderSettings) }
                )
            }
            GroupHeader(
                strategy = state.activeActivationStrategy,
                generationState = state.conversationState.generationState,
                canContinue = canContinue,
                hasAvailableProvider = state.hasAvailableProvider,
                onContinue = { emitIntent(GroupChatUiIntent.ContinueLast) }
            )
            MemberRail(
                members = state.members,
                selectedSpeakerId = state.conversationState.selectedSpeakerId,
                enabled = !generating,
                onSelect = {
                    emitIntent(GroupChatUiIntent.SelectSpeaker(it))
                },
                onToggleMuted = {
                    emitIntent(GroupChatUiIntent.ToggleMemberMuted(it))
                }
            )
            MessageList(
                messages = state.conversationState.messages,
                canLoadOlderMessages = state.conversationState.canLoadOlderMessages,
                isLoadingOlderMessages = state.conversationState.isLoadingOlderMessages,
                expandedThinkBlockIds = state.conversationState.expandedThinkBlockIds,
                editingMessageId = state.conversationState.editingMessageId,
                editingMessageDraft = state.conversationState.editingMessageDraft,
                emitIntent = emitIntent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChatSettingsView(
    state: GroupChatSettingsState,
    members: List<GroupChatMemberItem>,
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
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_basic_settings)
                ) {
                    RpScrollableOutlinedTextField(
                        value = state.titleDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeTitle(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_title_label)) },
                        singleLine = true
                    )
                    RpScrollableOutlinedTextField(
                        value = state.scenarioDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeScenario(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_scenario)) },
                        minLines = 3,
                        maxLines = 8
                    )
                    RpScrollableOutlinedTextField(
                        value = state.userNoteDraft,
                        onValueChange = { emitIntent(GroupChatUiIntent.ChangeUserNote(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_author_note)) },
                        minLines = 3,
                        maxLines = 8
                    )
                    RpScrollableOutlinedTextField(
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
                        GroupChatActivationStrategy.entries.forEach { strategy ->
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
                        GroupChatCharacterCardMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.characterCardMode == mode,
                                onClick = {
                                    emitIntent(GroupChatUiIntent.SelectCharacterCardMode(mode))
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            if (mode == GroupChatCharacterCardMode.Swap) {
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
                    RpScrollableOutlinedTextField(
                        value = state.systemPromptDraft,
                        onValueChange = {
                            emitIntent(GroupChatUiIntent.ChangeSystemPrompt(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_main_title)) },
                        minLines = 3,
                        maxLines = 10
                    )
                    RpScrollableOutlinedTextField(
                        value = state.groupNudgePromptDraft,
                        onValueChange = {
                            emitIntent(GroupChatUiIntent.ChangeGroupNudgePrompt(it))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_group_nudge_title)) },
                        minLines = 3,
                        maxLines = 10
                    )
                    RpScrollableOutlinedTextField(
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
                    GroupMemberSettingsList(members, emitIntent)
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
                        visibleGroups = state.visibleLorebookGroups,
                        query = state.lorebookQuery,
                        onQueryChange = {
                            emitIntent(GroupChatUiIntent.ChangeLorebookQuery(it))
                        },
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
                GroupSettingsSection(title = stringResource(R.string.chat_settings_actions)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.group_chat_delete_content_description),
                        subtitle = stringResource(R.string.delete_chat_desc),
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { emitIntent(GroupChatUiIntent.DeleteSessionClick) }
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
    iconTint: Color = MaterialTheme.colorScheme.primary,
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
                tint = iconTint
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

/** 在群聊设置卡片内渲染支持长按拖动的成员列表。 */
@Composable
private fun GroupMemberSettingsList(
    members: List<GroupChatMemberItem>,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    val currentMembers = rememberUpdatedState(members)
    val dragDropState = rememberGroupMemberDragDropState(
        onMove = { fromCharacterId, toCharacterId ->
            val current = currentMembers.value
            if (current.any { it.id == fromCharacterId } &&
                current.any { it.id == toCharacterId }
            ) {
                emitIntent(
                    GroupChatUiIntent.ReorderMember(fromCharacterId, toCharacterId)
                )
            }
        },
        onDragEnd = { emitIntent(GroupChatUiIntent.CommitMemberOrder) }
    )
    LaunchedEffect(members.map { it.id }) {
        dragDropState.retainCharacters(members.map { it.id }.toSet())
    }
    // 稳定组合键保证成员换位后，手势仍持续追踪同一角色
    members.forEachIndexed { index, member ->
        key(member.id) {
            DraggableGroupMemberSettingsRow(
                member = member,
                dragDropState = dragDropState,
                canMoveUp = index > 0,
                canMoveDown = index < members.lastIndex,
                canRemove = members.size > 2,
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
    }
}

/** 为单个成员行安装长按拖动手势与跟手位移。 */
@Composable
private fun DraggableGroupMemberSettingsRow(
    member: GroupChatMemberItem,
    dragDropState: GroupMemberDragDropState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val dragging = dragDropState.draggingCharacterId == member.id
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                dragDropState.updateBounds(
                    characterId = member.id,
                    top = coordinates.positionInParent().y,
                    height = coordinates.size.height.toFloat()
                )
            }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragDropState.dragOffset(member.id)
            }
            .pointerInput(dragDropState, member.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { dragDropState.onDragStart(member.id) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.onDrag(member.id, dragAmount.y)
                    },
                    onDragEnd = dragDropState::onDragEnd,
                    onDragCancel = dragDropState::onDragEnd
                )
            }
    ) {
        GroupMemberSettingsRow(
            member = member,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            canRemove = canRemove,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove
        )
    }
}

/** 创建成员卡片内部使用的长按拖动状态。 */
@Composable
private fun rememberGroupMemberDragDropState(
    onMove: (fromCharacterId: Long, toCharacterId: Long) -> Unit,
    onDragEnd: () -> Unit
): GroupMemberDragDropState {
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    return remember {
        GroupMemberDragDropState(
            mOnMove = { from, to -> currentOnMove.value(from, to) },
            mOnDragEnd = { currentOnDragEnd.value() }
        )
    }
}

/** 群聊设置卡片内成员行的长按拖动状态管理器。 */
private class GroupMemberDragDropState(
    private val mOnMove: (fromCharacterId: Long, toCharacterId: Long) -> Unit,
    private val mOnDragEnd: () -> Unit
) {
    var draggingCharacterId by mutableStateOf<Long?>(null)
        private set

    private val mBounds = mutableMapOf<Long, GroupMemberBounds>()
    private var mDraggedDelta by mutableFloatStateOf(0f)
    private var mInitialTop = 0f
    private var mLastTargetCharacterId: Long? = null

    /** 清理已从群聊移除成员遗留的布局边界。 */
    fun retainCharacters(characterIds: Set<Long>) {
        mBounds.keys.retainAll(characterIds)
    }

    /** 记录成员在设置卡片坐标系中的最新布局边界。 */
    fun updateBounds(characterId: Long, top: Float, height: Float) {
        val previousTop = mBounds[characterId]?.top
        mBounds[characterId] = GroupMemberBounds(top, height)
        if (characterId == draggingCharacterId && previousTop != null && previousTop != top) {
            mLastTargetCharacterId = null
        }
    }

    /** 计算正在拖动的成员相对最新布局位置所需的视觉偏移。 */
    fun dragOffset(characterId: Long): Float {
        if (characterId != draggingCharacterId) return 0f
        val currentTop = mBounds[characterId]?.top ?: return 0f
        return mInitialTop + mDraggedDelta - currentTop
    }

    /** 从已完成布局的成员行开始一次拖动。 */
    fun onDragStart(characterId: Long) {
        val bounds = mBounds[characterId] ?: return
        draggingCharacterId = characterId
        mInitialTop = bounds.top
        mDraggedDelta = 0f
        mLastTargetCharacterId = null
    }

    /** 根据成员行覆盖相邻行的程度触发一次内存换位。 */
    fun onDrag(characterId: Long, deltaY: Float) {
        if (draggingCharacterId != characterId) return
        mDraggedDelta += deltaY
        val draggingBounds = mBounds[characterId] ?: return
        val start = mInitialTop + mDraggedDelta
        val end = start + draggingBounds.height
        // 仅沿当前手指移动方向查找最近的相邻目标，避免一次事件跨越多行
        val target = if (deltaY >= 0f) {
            mBounds.asSequence()
                .filter { (id, bounds) -> id != characterId && bounds.top > draggingBounds.top }
                .minByOrNull { (_, bounds) -> bounds.top }
                ?.takeIf { (_, bounds) -> end > bounds.top + bounds.height * 0.20f }
        } else {
            mBounds.asSequence()
                .filter { (id, bounds) -> id != characterId && bounds.top < draggingBounds.top }
                .maxByOrNull { (_, bounds) -> bounds.top }
                ?.takeIf { (_, bounds) -> start < bounds.top + bounds.height * 0.80f }
        }
        val targetCharacterId = target?.key ?: return
        if (targetCharacterId == mLastTargetCharacterId) return
        mLastTargetCharacterId = targetCharacterId
        mOnMove(characterId, targetCharacterId)
    }

    /** 结束或取消拖动并提交最终成员顺序。 */
    fun onDragEnd() {
        if (draggingCharacterId != null) mOnDragEnd()
        draggingCharacterId = null
        mDraggedDelta = 0f
        mInitialTop = 0f
        mLastTargetCharacterId = null
    }
}

/** 成员行在设置卡片坐标系中的纵向边界。 */
private data class GroupMemberBounds(val top: Float, val height: Float)

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
    strategy: GroupChatActivationStrategy,
    generationState: GroupChatGenerationState,
    canContinue: Boolean,
    hasAvailableProvider: Boolean = true,
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
                            if (!hasAvailableProvider) stringResource(R.string.no_model_configured) else stringResource(
                                R.string.group_chat_cast_ready
                            )

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
    val hapticFeedback = LocalHapticFeedback.current
    val accent = getMacaronColor(member.name)
    Card(
        modifier = Modifier
            .width(138.dp)
            .alpha(if (member.muted) 0.55f else 1f)
            .clickable(
                enabled = enabled,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleMuted()
                },
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
    canLoadOlderMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    expandedThinkBlockIds: Set<String>,
    editingMessageId: Long?,
    editingMessageDraft: String,
    emitIntent: (GroupChatUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    val latestCanLoadOlderMessages by rememberUpdatedState(canLoadOlderMessages)
    val latestIsLoadingOlderMessages by rememberUpdatedState(isLoadingOlderMessages)
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var isFirstLoad by remember { mutableStateOf(true) }
    var isScrollIndicatorDragged by remember { mutableStateOf(false) }
    var lastTailMessageId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward to isScrollIndicatorDragged }
            .collect { (canScrollForward, indicatorDragged) ->
                if (indicatorDragged) {
                    shouldFollowBottom = false
                } else if (!canScrollForward) {
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

    // 首次定位到底部完成后，用户接近当前窗口顶部才请求更早历史
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to isFirstLoad }
            .distinctUntilChanged()
            .collect { (firstVisibleItemIndex, firstLoad) ->
                if (!firstLoad &&
                    firstVisibleItemIndex <= HISTORY_LOAD_THRESHOLD &&
                    latestCanLoadOlderMessages &&
                    !latestIsLoadingOlderMessages
                ) {
                    emitIntent(GroupChatUiIntent.LoadOlderMessages)
                }
            }
    }

    // 只有尾部消息身份改变时才滚到底部，头部加载历史不会打断用户位置
    val tailMessageId = messages.lastOrNull()?.id
    LaunchedEffect(tailMessageId) {
        if (tailMessageId == null) {
            lastTailMessageId = null
            isFirstLoad = true
        } else if (isFirstLoad || tailMessageId != lastTailMessageId) {
            shouldFollowBottom = true
            listState.scrollToItem(
                messages.size + if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0
            )
            isFirstLoad = false
        }
        lastTailMessageId = tailMessageId
    }

    // - 内容流式生成或思考块折叠变动时，若处于跟随状态则自动跟随到底部
    LaunchedEffect(
        messages.lastOrNull()?.content,
        expandedThinkBlockIds
    ) {
        if (messages.isNotEmpty()) {
            if (isFirstLoad || shouldFollowBottom) {
                listState.scrollToItem(
                    messages.size + if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0
                )
                isFirstLoad = false
            }
        }
    }
    if (messages.isEmpty()) {
        EmptyConversation(modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .draggableLazyListScrollIndicator(
                state = listState,
                onDragStateChanged = { dragging ->
                    isScrollIndicatorDragged = dragging
                    shouldFollowBottom = if (dragging) {
                        false
                    } else {
                        !listState.canScrollForward
                    }
                }
            ),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (canLoadOlderMessages || isLoadingOlderMessages) {
            item(key = "older-messages-loader") {
                OlderMessagesLoadIndicator(loading = isLoadingOlderMessages)
            }
        }
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 在消息窗口顶部保留稳定高度，并在读取历史时展示轻量进度。 */
@Composable
private fun OlderMessagesLoadIndicator(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
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
    val isUser = message.source == GroupChatMessageSource.User
    val isSystem = message.source == GroupChatMessageSource.System
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
                    isUser -> RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 4.dp,
                        bottomEnd = 18.dp,
                        bottomStart = 18.dp
                    )

                    isSystem -> RoundedCornerShape(14.dp)
                    else -> RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 18.dp,
                        bottomEnd = 18.dp,
                        bottomStart = 18.dp
                    )
                },
                color = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surface
                },
                border = when {
                    isUser -> BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                    isSystem -> BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )

                    else -> BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )
                },
                shadowElevation = if (isUser) 1.5.dp else 0.5.dp
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
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                            border = BorderStroke(
                                0.5.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (isThinking) indicatorColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (isThinking) {
                    GroupStreamingStatus(
                        text = stringResource(R.string.thinking),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = indicatorColor,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.thought_process),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.hide) else stringResource(
                        R.string.show
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = part.content,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
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
        RpScrollableOutlinedTextField(
            value = draft,
            onValueChange = {
                emitIntent(GroupChatUiIntent.ChangeEditingMessageDraft(it))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 1,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { emitIntent(GroupChatUiIntent.CancelEditingMessage) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { emitIntent(GroupChatUiIntent.SaveEditingMessage) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.save),
                    fontWeight = FontWeight.Bold,
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
            if (message.source == GroupChatMessageSource.Character) {
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
                    emitIntent(GroupChatUiIntent.DeleteMessageClick(message.id))
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
    val hapticFeedback = LocalHapticFeedback.current
    val sendButtonColor by animateColorAsState(
        targetValue = if (generating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "sendButtonColor"
    )

    Surface(
        tonalElevation = 5.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            RpScrollableOutlinedTextField(
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
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = sendButtonColor,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (generating) onStop()
                    else onSend()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (generating) {
                            Icons.Rounded.Stop
                        } else {
                            Icons.AutoMirrored.Rounded.Send
                        },
                        contentDescription = stringResource(
                            if (generating) R.string.stop else R.string.send
                        ),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    when (val dialogState = state.dialogState) {
        GroupChatDialogState.None -> Unit
        is GroupChatDialogState.SessionLorebook -> GroupSessionLorebookDialog(
            groups = state.settingsState.lorebookGroups,
            dialogState = dialogState,
            emitIntent = emitIntent
        )

        is GroupChatDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.OpenProviderSettings) }
        )

        is GroupChatDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            onCopyRequest = { emitIntent(GroupChatUiIntent.CopyPromptItem(it)) }
        )

        is GroupChatDialogState.DeleteMessageConfirm -> AppDangerDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.delete_message_title),
            message = stringResource(R.string.delete_message_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.ConfirmDeleteMessage) }
        )

        is GroupChatDialogState.DeleteSessionConfirm -> AppDangerDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.group_chat_delete_title),
            message = stringResource(
                R.string.group_chat_delete_message,
                dialogState.title
            ),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.ConfirmDeleteSession) }
        )
    }
}

/** 将群聊世界书状态适配到应用级快捷管理对话框。 */
@Composable
private fun GroupSessionLorebookDialog(
    groups: List<GroupChatLorebookGroupItem>,
    dialogState: GroupChatDialogState.SessionLorebook,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    // 通用 Dialog 只接收展示字段，确认后的会话级持久化仍由群聊状态层负责。
    val dialogGroups = remember(groups) {
        groups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    SessionLorebookDialog(
        groups = dialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        enabledEntryIds = dialogState.enabledEntryIds,
        onQueryChange = {
            emitIntent(GroupChatUiIntent.ChangeSessionLorebookDialogQuery(it))
        },
        onToggleGroup = {
            emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogGroup(it))
        },
        onToggleEntry = {
            emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogEntry(it))
        },
        onConfirmSelection = {
            emitIntent(GroupChatUiIntent.ConfirmSessionLorebookSelection)
        },
        onManageWorldBooks = { emitIntent(GroupChatUiIntent.OpenWorldBookManager) },
        onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) }
    )
}

/** 转换群聊世界书分组为通用对话框展示模型。 */
private fun GroupChatLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
    return SessionLorebookDialogGroup(
        id = lorebookId,
        name = lorebookName,
        entries = entries.map { entry ->
            SessionLorebookDialogEntry(
                id = entry.id,
                name = entry.name,
                content = entry.content,
                keywords = entry.keywords,
                constant = entry.constant
            )
        }
    )
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

private fun GroupChatActivationStrategy.titleRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual -> R.string.group_chat_strategy_manual
        GroupChatActivationStrategy.Natural -> R.string.group_chat_strategy_natural
        GroupChatActivationStrategy.List -> R.string.group_chat_strategy_list
        GroupChatActivationStrategy.Pooled -> R.string.group_chat_strategy_pooled
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
                members = previewMembers,
                activeActivationStrategy = GroupChatActivationStrategy.Natural,
                conversationState = GroupChatConversationState(
                    messages = previewMessages,
                    selectedSpeakerId = 1
                ),
                settingsState = GroupChatSettingsState(
                    activationStrategy = GroupChatActivationStrategy.Natural
                )
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
        source = GroupChatMessageSource.User,
        speakerName = "You",
        content = "The signal is coming from the abandoned station.",
        time = "21:04"
    ),
    GroupChatMessageItem(
        id = 2,
        source = GroupChatMessageSource.Character,
        speakerName = "Lyra",
        content = "Then we should approach quietly. Its navigation lights are still active.",
        time = "21:04"
    ),
    GroupChatMessageItem(
        id = 3,
        source = GroupChatMessageSource.Character,
        speakerName = "Mina",
        content = "I will check the archive for its last registered crew.",
        time = "21:05"
    )
)
