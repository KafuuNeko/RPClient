package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatGenerationState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.model.ChatWritingState
import me.kafuuneko.rpclient.ui.dialog.MessageImageViewerDialog
import me.kafuuneko.rpclient.ui.message.ChatWritingPanel
import me.kafuuneko.rpclient.ui.message.ConversationComposer
import me.kafuuneko.rpclient.ui.message.ConversationMessageActionButton
import me.kafuuneko.rpclient.ui.message.ConversationMessageAvatar
import me.kafuuneko.rpclient.ui.message.ConversationMessageBubble
import me.kafuuneko.rpclient.ui.message.ConversationMessageItem
import me.kafuuneko.rpclient.ui.message.ConversationMessageList
import me.kafuuneko.rpclient.ui.message.ConversationMessageRole
import me.kafuuneko.rpclient.ui.message.ConversationQuickAction
import me.kafuuneko.rpclient.ui.theme.NarratorAvatarColor
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.pageSurfaceBlur

/**
 * 渲染群聊会话主页，保留成员、消息列表与输入区的状态归属。
 *
 * @param state 当前群聊页面状态
 * @param emitIntent 原有群聊意图发送入口
 */
@Composable
internal fun GroupChatNormalPage(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    val conversation = state.conversationState
    // 生成状态继续决定输入区与续写操作的可用性。
    val generation = conversation.generationState
    val impersonating = generation as? GroupChatGenerationState.Impersonating
    val generating = generation is GroupChatGenerationState.Generating || impersonating != null
    val canContinue = conversation.hasCharacterMessage
    val displayMessages = remember(conversation.messages, impersonating?.preview) {
        impersonating?.preview?.let { preview -> conversation.messages + preview }
            ?: conversation.messages
    }

    // 顶栏、成员轨、共享消息列表与输入区各自消费对应安全区，Scaffold 本身不重复追加系统栏留白。
    AppPageScaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(left = 0.dp),
        topBar = {
            CustomGroupChatTopBar(
                title = state.title,
                strategy = state.activeActivationStrategy,
                generationState = conversation.generationState,
                hasAvailableProvider = state.hasAvailableProvider,
                hasPromptInspection = state.hasPromptInspection,
                sessionLoreDialogVisible = state.dialogState is GroupChatDialogState.SessionLorebook,
                generating = generating,
                onBack = { emitIntent(GroupChatUiIntent.Back) },
                emitIntent = emitIntent,
            )
        },
        bottomBar = {
            Column {
                if ((conversation.generationState as? GroupChatGenerationState.Failed)?.canRetryReply == true) {
                    TextButton(
                        onClick = { emitIntent(GroupChatUiIntent.RetryImageReply) },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(stringResource(R.string.image_retry))
                    }
                }
                MessageImageViewerDialog(
                    state = state.imageState,
                    emit = { action ->
                        emitIntent(GroupChatUiIntent.ImageAction(action = action))
                    },
                )
                Composer(
                    draft = conversation.inputDraft,
                    generating = generating,
                    writingState = impersonating?.writing,
                    onToggleReasoning = { emitIntent(GroupChatUiIntent.ToggleWritingReasoning) },
                    onImpersonate = { emitIntent(GroupChatUiIntent.ImpersonateUser) },
                    imageState = state.imageState,
                    onDraftChange = { text ->
                        emitIntent(GroupChatUiIntent.ChangeInputDraft(text))
                    },
                    onSend = { emitIntent(GroupChatUiIntent.SendMessage) },
                    onStop = { emitIntent(GroupChatUiIntent.StopGeneration) },
                    onImageAction = { action -> emitIntent(GroupChatUiIntent.ImageAction(action)) },
                    canContinue = canContinue,
                    onContinue = { emitIntent(GroupChatUiIntent.ContinueLast) },
                    onSummarize = { emitIntent(GroupChatUiIntent.SummarizeNow) },
                )
            }
        },
        content = { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (!state.hasAvailableProvider) {
                    NoProviderBanner(onClick = { emitIntent(GroupChatUiIntent.OpenProviderSettings) })
                }
                MemberRail(
                    members = state.members,
                    selectedSpeakerId = conversation.selectedSpeakerId,
                    enabled = !generating,
                    onSelect = { id ->
                        emitIntent(GroupChatUiIntent.SelectSpeaker(id))
                    },
                    onToggleMuted = { id ->
                        emitIntent(GroupChatUiIntent.ToggleMemberMuted(id))
                    },
                )
                ConversationMessageList(
                    messages = displayMessages,
                    itemId = { message -> message.id.toString() },
                    itemContentType = { message -> message.source },
                    itemContentKey = { message -> message.content },
                    canLoadOlderMessages = conversation.canLoadOlderMessages,
                    historyLoadState = conversation.historyLoadState,
                    searchAnchorMessageId = conversation.searchAnchorMessageId?.toString(),
                    canLoadNewerMessages = conversation.canLoadNewerMessages,
                    expandedThinkBlockIds = conversation.expandedThinkBlockIds,
                    onLoadOlderMessages = { emitIntent(GroupChatUiIntent.LoadOlderMessages) },
                    onLoadNewerMessages = { emitIntent(GroupChatUiIntent.LoadNewerMessages) },
                    onReturnToLatest = { emitIntent(GroupChatUiIntent.ReturnToLatest) },
                    onReachLatestMessage = { message ->
                        emitIntent(GroupChatUiIntent.ReachLatestMessage(message.id))
                    },
                    modifier = Modifier.weight(1f),
                    emptyContent = { emptyModifier ->
                        EmptyConversation(modifier = emptyModifier)
                    },
                    messageContent = { _, message ->
                        val isEditing = conversation.editingMessageId == message.id
                        GroupChatMessageBubble(
                            imageState = state.imageState,
                            message = message,
                            editing = isEditing,
                            editingDraft = conversation.editingMessageDraft
                                .takeIf { isEditing }
                                .orEmpty(),
                            expandedThinkBlockIds = conversation.expandedThinkBlockIds,
                            emitIntent = emitIntent,
                        )
                    },
                )
            }
        },
    )
}

/**
 * 群聊专用顶部栏。
 * - 整合返回导航、群聊标题、发言顺序策略与生成状态描述。
 * - 生成过程中在副标题旁展示微型进度指示。
 * - 右侧图标尺寸、透明度与入口样式与单聊保持一致。
 */
@Composable
private fun CustomGroupChatTopBar(
    title: String,
    strategy: GroupChatActivationStrategy,
    generationState: GroupChatGenerationState,
    hasAvailableProvider: Boolean,
    hasPromptInspection: Boolean,
    sessionLoreDialogVisible: Boolean,
    generating: Boolean,
    onBack: () -> Unit,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    val strategyText = stringResource(
        R.string.group_chat_turn_order,
        stringResource(strategy.titleRes()),
    )
    val statusText = when (generationState) {
        GroupChatGenerationState.Idle ->
            if (!hasAvailableProvider) stringResource(R.string.no_model_configured)
            else stringResource(R.string.group_chat_cast_ready)

        is GroupChatGenerationState.Generating ->
            stringResource(
                R.string.group_chat_speaker_replying,
                generationState.speakerName,
                generationState.current,
                generationState.total,
            )

        is GroupChatGenerationState.Impersonating -> stringResource(R.string.impersonate_user)
        is GroupChatGenerationState.Failed -> generationState.message
    }

    // 采用与单聊一致的顶部底板与毛玻璃样式。
    PageSurface(
        kind = PageSurfaceKind.TopBar,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageTopBarOpacity.current),
        tonalElevation = 1.dp * LocalPageTopBarOpacity.current,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 返回上一页入口。
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 群聊标题与副状态（发言策略与当前状态合一）。
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "$strategyText • $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(weight = 1f, fill = false),
                    )
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }
            }

            // 顶部操作区：沿用单聊的按钮尺寸、图标与不透明度层级。
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.OpenPromptInspector) },
                enabled = hasPromptInspection,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.prompt_inspector_title),
                    modifier = Modifier.size(20.dp),
                    tint = if (hasPromptInspection) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.ShowSessionLoreDialog) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Book,
                    contentDescription = stringResource(R.string.session_world_books),
                    modifier = Modifier.size(20.dp),
                    tint = if (sessionLoreDialogVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    },
                )
            }
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.OpenSettings) },
                enabled = !generating,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(20.dp),
                    tint = if (!generating) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
}

/** 单行横向滚动的轻量成员轨，横屏下自然平铺展开并避让水平安全区。 */
@Composable
private fun MemberRail(
    members: List<GroupChatMemberItem>,
    selectedSpeakerId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onToggleMuted: (Long) -> Unit,
) {
    // 成员选择与静音操作沿用原有回调，横向列表只负责呈现。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            )
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        members.forEach { member ->
            MemberChip(
                member = member,
                selected = selectedSpeakerId == member.id,
                enabled = enabled,
                onSelect = { onSelect(member.id) },
                onToggleMuted = { onToggleMuted(member.id) },
            )
        }
    }
}

/** 紧凑型成员芯片，支持指定发言人高亮与快速静音切换。 */
@Composable
private fun MemberChip(
    member: GroupChatMemberItem,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onToggleMuted: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val accent = getMacaronColor(member.name)
    val shape = RoundedCornerShape(14.dp)

    // 底板随选定与静音状态切换对比度；采用修饰符点击避免 Material Surface 强制扩展 48dp 最小交互区域引起上下多余模糊。
    AppSurface(
        modifier = Modifier
            .pageSurfaceBlur(PageSurfaceKind.Bubble, shape)
            .alpha(if (member.muted) 0.55f else 1f)
            .clickable(
                enabled = enabled,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                },
            ),
        shape = shape,
        border = if (selected) BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = LocalPageBubbleOpacity.current),
        ) else BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f * LocalPageBubbleOpacity.current),
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f * LocalPageBubbleOpacity.current)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f * LocalPageBubbleOpacity.current)
        },
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 成员头像
            RpAvatar(
                text = member.name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                modifier = Modifier.size(22.dp),
                image = member.avatarImage,
            )
            // 成员名称
            Text(
                text = member.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 静音状态轻量切换按钮，避免使用默认 48dp 交互区域的 IconButton 撑大芯片上下边距产生空余模糊底板
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleMuted()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (member.muted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(if (member.muted) R.string.unmute else R.string.mute),
                    modifier = Modifier.size(13.dp),
                    tint = if (member.muted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier) {
    // 空会话提示占用原有内容区域，保留输入区布局。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppSurface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.group_chat_room_ready),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.group_chat_room_ready_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 将群聊消息模型与意图回调接入公共消息气泡。 */
@Composable
private fun GroupChatMessageBubble(
    imageState: MessageImageState,
    message: GroupChatMessageItem,
    editing: Boolean,
    editingDraft: String,
    expandedThinkBlockIds: Set<String>,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    val item = remember(message) { message.toConversationMessageItem() }
    // 群聊消息完全复用单聊标准的气泡与外置悬浮操作栏，只注入群聊支持的业务操作。
    ConversationMessageBubble(
        message = item,
        imageState = imageState,
        expandedThinkBlockIds = expandedThinkBlockIds,
        editing = editing,
        editingDraft = editingDraft,
        onToggleThinkBlock = { blockId ->
            emitIntent(GroupChatUiIntent.ToggleThinkBlock(blockId))
        },
        onEditingDraftChange = { text ->
            emitIntent(GroupChatUiIntent.ChangeEditingMessageDraft(text))
        },
        onSaveEdit = { emitIntent(GroupChatUiIntent.SaveEditingMessage) },
        onCancelEdit = { emitIntent(GroupChatUiIntent.CancelEditingMessage) },
        onImageAction = { action -> emitIntent(GroupChatUiIntent.ImageAction(action)) },
        actions = {
            GroupChatMessageActions(
                message = message,
                emitIntent = emitIntent,
            )
        },
    )
}

/** 将群聊消息展示模型映射为公共消息气泡展示模型。 */
private fun GroupChatMessageItem.toConversationMessageItem(): ConversationMessageItem {
    val visualRole = when (source) {
        GroupChatMessageSource.User -> ConversationMessageRole.User
        GroupChatMessageSource.Character -> ConversationMessageRole.Assistant
        GroupChatMessageSource.System -> ConversationMessageRole.Narrator
    }
    // 角色消息使用各自头像与稳定马卡龙强调色，系统消息与单聊旁白视觉保持一致。
    val avatar = when (source) {
        GroupChatMessageSource.User -> null
        GroupChatMessageSource.Character -> ConversationMessageAvatar(
            text = speakerName.firstOrNull()?.uppercase() ?: "?",
            accentColor = getMacaronColor(speakerName),
            image = avatarImage,
        )

        GroupChatMessageSource.System -> ConversationMessageAvatar(
            text = speakerName.firstOrNull()?.uppercase() ?: "?",
            accentColor = NarratorAvatarColor,
            image = null,
        )
    }
    return ConversationMessageItem(
        id = id.toString(),
        role = visualRole,
        speaker = speakerName,
        content = content,
        parts = parts,
        time = time,
        imageUuids = imageUuids,
        isStreaming = isStreaming,
        avatar = avatar,
    )
}

/** 提供群聊消息悬浮操作栏中的业务按钮。 */
@Composable
private fun RowScope.GroupChatMessageActions(
    message: GroupChatMessageItem,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 全部消息支持复制、编辑与删除，角色回复额外开放单条重生成。
    ConversationMessageActionButton(
        icon = Icons.Rounded.ContentCopy,
        contentDescription = stringResource(R.string.copy),
        onClick = { emitIntent(GroupChatUiIntent.CopyMessage(message.id)) },
    )
    ConversationMessageActionButton(
        icon = Icons.Rounded.Edit,
        contentDescription = stringResource(R.string.edit),
        onClick = { emitIntent(GroupChatUiIntent.StartEditMessage(message.id)) },
    )
    if (message.source == GroupChatMessageSource.Character) {
        ConversationMessageActionButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.regenerate),
            onClick = { emitIntent(GroupChatUiIntent.RegenerateMessage(message.id)) },
        )
    }
    ConversationMessageActionButton(
        icon = Icons.Rounded.Delete,
        contentDescription = stringResource(R.string.delete),
        onClick = { emitIntent(GroupChatUiIntent.DeleteMessageClick(message.id)) },
    )
}

/** 群聊输入意图与快捷操作接入公共输入区。 */
@Composable
private fun Composer(
    draft: String,
    generating: Boolean,
    writingState: ChatWritingState?,
    onToggleReasoning: () -> Unit,
    onImpersonate: () -> Unit,
    imageState: MessageImageState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onSummarize: () -> Unit,
) {
    // 两类会话共享全部布局和配色，群聊仅提供自身支持的操作。
    ConversationComposer(
        draft = draft,
        placeholder = stringResource(R.string.group_chat_message_hint),
        imageState = imageState,
        isGenerating = generating,
        inputEnabled = !generating || writingState != null,
        readOnly = writingState != null,
        beforeActions = {
            writingState?.let { ChatWritingPanel(it, onToggleReasoning) }
        },
        onDraftChange = onDraftChange,
        onSend = onSend,
        onStop = onStop,
        onImageAction = onImageAction,
        actions = {
            ConversationQuickAction(
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.continue_latest_reply),
                enabled = canContinue && !generating,
                onClick = onContinue,
            )
            ConversationQuickAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.impersonate_user),
                enabled = !generating && !imageState.loadState.isBusy,
                onClick = onImpersonate,
            )
            ConversationQuickAction(
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.summarize_now),
                enabled = !generating && !imageState.loadState.isSubmitting,
                onClick = onSummarize,
            )
        },
    )
}
