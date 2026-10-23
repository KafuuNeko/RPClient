package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatCharacterCardMode
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.groupchat.GroupChatLorebookSelector
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch

/**
 * 渲染群聊设置页，继续通过原有意图编辑和保存设置草稿。
 *
 * @param state 当前设置草稿状态
 * @param members 当前群聊成员
 * @param emitIntent 原有群聊意图发送入口
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupChatSettingsPage(
    state: GroupChatSettingsState,
    members: List<GroupChatMemberItem>,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 设置草稿仍由页面状态持有，保存按钮和各字段沿用原有意图。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = stringResource(R.string.group_chat_settings),
            onBack = { emitIntent(GroupChatUiIntent.CloseSettings) },
            actions = {
                TextButton(onClick = { emitIntent(GroupChatUiIntent.SaveSettings) }) {
                    Text(stringResource(R.string.save))
                }
            },
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_basic_settings),
                ) {
                    RpScrollableOutlinedTextField(
                        value = state.titleDraft,
                        onValueChange = { text -> emitIntent(GroupChatUiIntent.ChangeTitle(text)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_title_label)) },
                        singleLine = true,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.scenarioDraft,
                        onValueChange = { text -> emitIntent(GroupChatUiIntent.ChangeScenario(text)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_scenario)) },
                        minLines = 3,
                        maxLines = 8,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.userNoteDraft,
                        onValueChange = { text -> emitIntent(GroupChatUiIntent.ChangeUserNote(text)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.group_chat_author_note)) },
                        minLines = 3,
                        maxLines = 8,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.summaryDraft,
                        onValueChange = { text -> emitIntent(GroupChatUiIntent.ChangeSummary(text)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.summary_memory)) },
                        minLines = 4,
                        maxLines = 10,
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.summarize_now),
                        subtitle = stringResource(R.string.summarize_now_desc),
                        onClick = { emitIntent(GroupChatUiIntent.SummarizeNow) },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restore_previous_summary),
                        subtitle = stringResource(R.string.restore_previous_summary_desc),
                        onClick = {
                            emitIntent(GroupChatUiIntent.RestorePreviousSummary)
                        },
                    )
                    SettingsSwitchRow(
                        title = stringResource(R.string.pause_auto_summary),
                        subtitle = stringResource(R.string.pause_auto_summary_desc),
                        checked = state.autoSummaryPaused,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatUiIntent.ToggleAutoSummaryPaused(enabled))
                        },
                    )
                }
            }
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_turn_strategy),
                ) {
                    Text(
                        text = stringResource(R.string.group_chat_turn_strategy),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GroupChatActivationStrategy.entries.forEach { strategy ->
                            FilterChip(
                                selected = state.activationStrategy == strategy,
                                onClick = {
                                    emitIntent(GroupChatUiIntent.SelectActivationStrategy(strategy))
                                },
                                label = { Text(stringResource(strategy.titleRes())) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.group_chat_character_cards),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GroupChatCharacterCardMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.characterCardMode == mode,
                                onClick = {
                                    emitIntent(GroupChatUiIntent.SelectCharacterCardMode(mode))
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            id = if (mode == GroupChatCharacterCardMode.Swap) {
                                                R.string.group_chat_card_mode_swap
                                            } else {
                                                R.string.group_chat_card_mode_join
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_include_muted_cards),
                        checked = state.includeMutedCards,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatUiIntent.ToggleIncludeMutedCards(enabled))
                        },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_allow_consecutive),
                        checked = state.allowSelfResponses,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatUiIntent.ToggleAllowSelfResponses(enabled))
                        },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_auto_mode),
                        checked = state.autoModeEnabled,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatUiIntent.ToggleAutoMode(enabled))
                        },
                    )
                    SettingsSwitch(
                        title = stringResource(R.string.group_chat_trim_other_speakers),
                        checked = state.trimOtherSpeakers,
                        onCheckedChange = { enabled ->
                            emitIntent(GroupChatUiIntent.ToggleTrimOtherSpeakers(enabled))
                        },
                    )
                }
            }
            item {
                GroupSettingsSection(
                    title = stringResource(R.string.group_chat_prompt_overrides),
                ) {
                    Text(
                        text = stringResource(R.string.group_chat_prompt_overrides_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.systemPromptDraft,
                        onValueChange = { text ->
                            emitIntent(GroupChatUiIntent.ChangeSystemPrompt(text))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_main_title)) },
                        minLines = 3,
                        maxLines = 10,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.groupNudgePromptDraft,
                        onValueChange = { text ->
                            emitIntent(GroupChatUiIntent.ChangeGroupNudgePrompt(text))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.prompt_group_nudge_title)) },
                        minLines = 3,
                        maxLines = 10,
                    )
                    RpScrollableOutlinedTextField(
                        value = state.newGroupChatPromptDraft,
                        onValueChange = { text ->
                            emitIntent(GroupChatUiIntent.ChangeNewGroupChatPrompt(text))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(stringResource(R.string.prompt_new_group_chat_title))
                        },
                        minLines = 2,
                        maxLines = 8,
                    )
                }
            }
            item {
                GroupSettingsSection(title = stringResource(R.string.group_chat_members)) {
                    GroupMemberSettingsList(members, emitIntent)
                    state.availableCharacters
                        .filterNot { groupChatAvailableCharacterItem -> groupChatAvailableCharacterItem.alreadyMember }
                        .forEach { character ->
                            SettingsActionRow(
                                icon = Icons.Rounded.Groups,
                                title = stringResource(
                                    R.string.group_chat_add_member,
                                    character.name,
                                ),
                                onClick = {
                                    emitIntent(GroupChatUiIntent.AddMember(character.id))
                                },
                            )
                        }
                }
            }
            item {
                GroupSettingsSection(title = stringResource(R.string.world_book)) {
                    Text(
                        text = stringResource(R.string.session_lore_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GroupChatLorebookSelector(
                        groups = state.lorebookGroups,
                        visibleGroups = state.visibleLorebookGroups,
                        query = state.lorebookQuery,
                        onQueryChange = { text ->
                            emitIntent(GroupChatUiIntent.ChangeLorebookQuery(text))
                        },
                        onToggleLorebook = { id ->
                            emitIntent(GroupChatUiIntent.ToggleLorebook(id))
                        },
                        onToggleEntry = { id ->
                            emitIntent(GroupChatUiIntent.ToggleLorebookEntry(id))
                        },
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
                        onClick = { emitIntent(GroupChatUiIntent.DeleteSessionClick) },
                    )
                }
            }
            item {
                Button(
                    onClick = { emitIntent(GroupChatUiIntent.SaveSettings) },
                    modifier = Modifier.fillMaxWidth(),
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
    content: @Composable ColumnScope.() -> Unit,
) {
    // 各设置分区沿用统一表面，内容由调用方提供。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RpSectionHeader(title = title)
            content()
        }
    }
}

/** 展示带图标、标题和可选说明的设置操作项。 */
@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    // 整行点击与图标颜色共享可用状态。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onCheckedChange: (Boolean) -> Unit,
) {
    // 开关状态由设置草稿提供，交互只回传修改意图。
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 在群聊设置卡片内渲染支持长按拖动的成员列表。 */
@Composable
private fun GroupMemberSettingsList(
    members: List<GroupChatMemberItem>,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    val currentMembers = rememberUpdatedState(members)
    val dragDropState = rememberGroupMemberDragDropState(
        onMove = { fromCharacterId, toCharacterId ->
            val current = currentMembers.value
            if (current.any { groupChatMemberItem -> groupChatMemberItem.id == fromCharacterId }
                && current.any { groupChatMemberItem -> groupChatMemberItem.id == toCharacterId }
            ) {
                emitIntent(GroupChatUiIntent.ReorderMember(fromCharacterId, toCharacterId))
            }
        },
        onDragEnd = { emitIntent(GroupChatUiIntent.CommitMemberOrder) },
    )
    LaunchedEffect(members.map { it.id }) {
        dragDropState.retainCharacters(members.map { groupChatMemberItem -> groupChatMemberItem.id }.toSet())
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
                    emitIntent(GroupChatUiIntent.MoveMember(characterId = member.id, offset = -1))
                },
                onMoveDown = {
                    emitIntent(GroupChatUiIntent.MoveMember(characterId = member.id, offset = 1))
                },
                onRemove = {
                    emitIntent(GroupChatUiIntent.RemoveMember(member.id))
                },
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
    onRemove: () -> Unit,
) {
    // 拖动偏移和层级沿用当前拖动状态，成员操作继续交由原回调。
    val dragging = dragDropState.draggingCharacterId == member.id
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned(
                onGloballyPositioned = { coordinates ->
                    dragDropState.updateBounds(
                        characterId = member.id,
                        top = coordinates.positionInParent().y,
                        height = coordinates.size.height.toFloat(),
                    )
                },
            )
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
                    onDragCancel = dragDropState::onDragEnd,
                )
            },
    ) {
        GroupMemberSettingsRow(
            member = member,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            canRemove = canRemove,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onRemove = onRemove,
        )
    }
}

/** 创建成员卡片内部使用的长按拖动状态。 */
@Composable
private fun rememberGroupMemberDragDropState(
    onMove: (fromCharacterId: Long, toCharacterId: Long) -> Unit,
    onDragEnd: () -> Unit,
): GroupMemberDragDropState {
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    return remember {
        GroupMemberDragDropState(
            mOnMove = { from, to -> currentOnMove.value(from, to) },
            mOnDragEnd = { currentOnDragEnd.value() },
        )
    }
}

/** 群聊设置卡片内成员行的长按拖动状态管理器。 */
private class GroupMemberDragDropState(
    private val mOnMove: (fromCharacterId: Long, toCharacterId: Long) -> Unit,
    private val mOnDragEnd: () -> Unit
) {
    var draggingCharacterId by mutableStateOf<Long?>(value = null)
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
    onRemove: () -> Unit,
) {
    // 成员静音与移除沿用原有意图及可用条件。
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpAvatar(
                text = member.name.take(1),
                color = getMacaronColor(member.name),
                modifier = Modifier.size(38.dp),
                image = member.avatarImage,
            )
            Text(
                text = member.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = null)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = null)
            }
            IconButton(onClick = onRemove, enabled = canRemove) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = if (canRemove) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // 整行点击与开关点击使用同一状态修改回调。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
