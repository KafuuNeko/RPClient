package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
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
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.dialog.MessageImageViewerDialog
import me.kafuuneko.rpclient.ui.message.DraftAttachmentTray
import me.kafuuneko.rpclient.ui.message.MessageImageEditButton
import me.kafuuneko.rpclient.ui.message.MessageImageGallery
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBottomBarOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.pageSurfaceBlur
import me.kafuuneko.rpclient.ui.widgets.pageUserBubbleContentColor
import androidx.compose.material.icons.rounded.Image as ImageIcon
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button

/**
 * 渲染群聊会话主页，保留成员、消息列表与输入区的状态归属。
 *
 * @param state 当前群聊页面状态
 * @param emitIntent 原有群聊意图发送入口
 */
@Composable
internal fun GroupChatNormalPage(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    // 生成状态继续决定输入区与续写操作的可用性。
    val generating = state.conversationState.generationState is GroupChatGenerationState.Generating
    val canContinue = state.conversationState.hasCharacterMessage
    AppPageScaffold(
        containerColor = Color.Transparent,
        topBar = {
            CustomGroupChatTopBar(
                title = state.title,
                strategy = state.activeActivationStrategy,
                generationState = state.conversationState.generationState,
                hasAvailableProvider = state.hasAvailableProvider,
                hasPromptInspection = state.hasPromptInspection,
                sessionLoreDialogVisible = state.dialogState is GroupChatDialogState.SessionLorebook,
                generating = generating,
                onBack = { emitIntent(GroupChatUiIntent.Back) },
                emitIntent = emitIntent
            )
        },
        bottomBar = {
            Column {
                if ((state.conversationState.generationState as? GroupChatGenerationState.Failed)?.canRetryReply == true) {
                    TextButton(
                        onClick = { emitIntent(GroupChatUiIntent.RetryImageReply) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.image_retry))
                    }
                }
                MessageImageViewerDialog(state.imageState) {
                    emitIntent(
                        GroupChatUiIntent.ImageAction(
                            it
                        )
                    )
                }
                Composer(
                    draft = state.conversationState.inputDraft,
                    generating = generating,
                    imageState = state.imageState,
                    onDraftChange = {
                        emitIntent(GroupChatUiIntent.ChangeInputDraft(it))
                    },
                    onSend = { emitIntent(GroupChatUiIntent.SendMessage) },
                    onStop = { emitIntent(GroupChatUiIntent.StopGeneration) },
                    onImageAction = { emitIntent(GroupChatUiIntent.ImageAction(it)) },
                    canContinue = canContinue,
                    onContinue = { emitIntent(GroupChatUiIntent.ContinueLast) },
                    onSummarize = { emitIntent(GroupChatUiIntent.SummarizeNow) }
                )
            }
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
                imageState = state.imageState,
                messages = state.conversationState.messages,
                canLoadOlderMessages = state.conversationState.canLoadOlderMessages,
                isLoadingOlderMessages = state.conversationState.isLoadingOlderMessages,
                searchAnchorMessageId = state.conversationState.searchAnchorMessageId,
                canLoadNewerMessages = state.conversationState.canLoadNewerMessages,
                isLoadingNewerMessages = state.conversationState.isLoadingNewerMessages,
                expandedThinkBlockIds = state.conversationState.expandedThinkBlockIds,
                editingMessageId = state.conversationState.editingMessageId,
                editingMessageDraft = state.conversationState.editingMessageDraft,
                emitIntent = emitIntent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 当前窗口顶部进入该范围时预取更早消息。 */
private const val HISTORY_LOAD_THRESHOLD = 4

/**
 * 群聊专用顶部栏。
 * - 整合返回导航、群聊标题、发言顺序策略与生成状态描述。
 * - 生成过程中在副标题旁展示微型进度指示。
 * - 右侧保留 Prompt 检查、会话世界书与设置入口。
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
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    val strategyText = stringResource(
        R.string.group_chat_turn_order,
        stringResource(strategy.titleRes())
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
                generationState.total
            )

        is GroupChatGenerationState.Failed -> generationState.message
    }

    // 采用与单聊一致的顶部底板与毛玻璃样式。
    PageSurface(
        kind = PageSurfaceKind.TopBar,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageTopBarOpacity.current),
        tonalElevation = 1.dp * LocalPageTopBarOpacity.current
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回上一页入口。
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$strategyText • $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (generationState is GroupChatGenerationState.Generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                    }
                }
            }

            // 顶部操作区：Prompt 检查、会话世界书与群设置。
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.OpenPromptInspector) },
                enabled = hasPromptInspection,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.prompt_inspector_title),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.ShowSessionLoreDialog) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Rounded.Book,
                    contentDescription = stringResource(R.string.session_world_books),
                    modifier = Modifier.size(20.dp),
                    tint = if (sessionLoreDialogVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            IconButton(
                onClick = { emitIntent(GroupChatUiIntent.OpenSettings) },
                enabled = !generating,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** 单行横向滚动的轻量成员轨，横屏下自然平铺展开。 */
@Composable
private fun MemberRail(
    members: List<GroupChatMemberItem>,
    selectedSpeakerId: Long?,
    enabled: Boolean,
    onSelect: (Long) -> Unit,
    onToggleMuted: (Long) -> Unit
) {
    // 成员选择与静音操作沿用原有回调，横向列表只负责呈现。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
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

/** 紧凑型成员芯片，支持指定发言人高亮与快速静音切换。 */
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
                }
            ),
        shape = shape,
        border = if (selected) BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = LocalPageBubbleOpacity.current)
        ) else BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f * LocalPageBubbleOpacity.current)
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f * LocalPageBubbleOpacity.current)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f * LocalPageBubbleOpacity.current)
        }
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 成员头像
            RpAvatar(
                text = member.name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                modifier = Modifier.size(22.dp),
                image = member.avatarImage
            )
            // 成员名称
            Text(
                text = member.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                        }
                    ),
                contentAlignment = Alignment.Center
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
                    modifier = Modifier.size(13.dp),
                    tint = if (member.muted) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageList(
    imageState: MessageImageState,
    messages: List<GroupChatMessageItem>,
    canLoadOlderMessages: Boolean,
    isLoadingOlderMessages: Boolean,
    searchAnchorMessageId: Long?,
    canLoadNewerMessages: Boolean,
    isLoadingNewerMessages: Boolean,
    expandedThinkBlockIds: Set<String>,
    editingMessageId: Long?,
    editingMessageDraft: String,
    emitIntent: (GroupChatUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSearchAnchor by rememberUpdatedState(searchAnchorMessageId)
    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    val latestCanLoadOlderMessages by rememberUpdatedState(canLoadOlderMessages)
    val latestIsLoadingOlderMessages by rememberUpdatedState(isLoadingOlderMessages)
    // 与列表位置一起恢复，返回设置前的历史阅读位置不应被首次定位或流式跟随覆盖。
    var shouldFollowBottom by rememberSaveable { mutableStateOf(true) }
    var isFirstLoad by rememberSaveable { mutableStateOf(true) }
    var isScrollIndicatorDragged by remember { mutableStateOf(false) }
    var lastTailMessageId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastSearchAnchor by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(listState) {
        // 恢复列表尚未完成测量时 canScrollForward 暂为 false，不能据此重新开启底部跟随。
        snapshotFlow {
            if (listState.layoutInfo.visibleItemsInfo.isEmpty()) null
            else listState.canScrollForward to isScrollIndicatorDragged
        }.collect { position ->
            val (canScrollForward, indicatorDragged) = position ?: return@collect
            if (indicatorDragged) {
                shouldFollowBottom = false
            } else if (activeSearchAnchor == null && !canScrollForward) {
                shouldFollowBottom = true
            }
        }
    }
    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            snapshotFlow { listState.canScrollForward }
                .collect { canScrollForward ->
                    shouldFollowBottom = activeSearchAnchor == null && !canScrollForward
                }
        }
    }

    val currentMessages by rememberUpdatedState(messages)
    val latestCanLoadNewerMessages by rememberUpdatedState(canLoadNewerMessages)
    val latestIsLoadingNewerMessages by rememberUpdatedState(isLoadingNewerMessages)
    LaunchedEffect(listState) {
        // 页头加载项也计入布局数量，防止分页后旧测量结果提前结束历史定位。
        snapshotFlow {
            val headerCount =
                if (latestCanLoadOlderMessages || latestIsLoadingOlderMessages) 1 else 0
            val layout = listState.layoutInfo
            currentMessages.lastOrNull()?.id?.takeIf {
                !isFirstLoad && activeSearchAnchor != null &&
                        !latestCanLoadNewerMessages && !latestIsLoadingNewerMessages &&
                        layout.totalItemsCount == currentMessages.size + headerCount + 1 &&
                        layout.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward
            }
        }.filterNotNull().distinctUntilChanged().collect { messageId ->
            emitIntent(GroupChatUiIntent.ReachLatestMessage(messageId))
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
    LaunchedEffect(tailMessageId, activeSearchAnchor) {
        if (activeSearchAnchor != null) {
            // 只在首次进入定位窗口时滚动，继续分页和流式刷新保持阅读位置。
            if (isFirstLoad) {
                val targetIndex = messages.indexOfFirst { it.id == activeSearchAnchor }
                if (targetIndex >= 0) listState.scrollToItem(targetIndex + (if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0))
                isFirstLoad = false
            }
            shouldFollowBottom = false
        } else if (tailMessageId == null) {
            isFirstLoad = true
        } else if (isFirstLoad || tailMessageId != lastTailMessageId || lastSearchAnchor != null) {
            shouldFollowBottom = true
            listState.scrollToItem(messages.size + (if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0))
            isFirstLoad = false
        }
        lastTailMessageId = tailMessageId
        lastSearchAnchor = activeSearchAnchor
    }

    // - 内容流式生成或思考块折叠变动时，若处于跟随状态则自动跟随到底部
    LaunchedEffect(
        messages.lastOrNull()?.content,
        expandedThinkBlockIds
    ) {
        if (messages.isNotEmpty()) {
            if (activeSearchAnchor == null && (isFirstLoad || shouldFollowBottom)) {
                listState.scrollToItem(
                    messages.size + if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0
                )
                isFirstLoad = false
            }
        }
    }

    // 记录列表视口高度，在软键盘弹出/收起导致视口尺寸变化时，将差值转化为滚动偏移，
    // 从而使当前查看的消息被键盘等高顶起，保持位置不变，顶部溢出部分在标题栏下方自然裁切
    val coroutineScope = rememberCoroutineScope()
    var previousListHeight by remember { mutableIntStateOf(0) }
    if (messages.isEmpty()) {
        EmptyConversation(modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                val newHeight = size.height
                if (activeSearchAnchor == null && previousListHeight > 0 && newHeight > 0 && newHeight != previousListHeight) {
                    val delta = (previousListHeight - newHeight).toFloat()
                    coroutineScope.launch {
                        // 尺寸回调仍处于测量阶段，下一帧再滚动以免重入布局。
                        withFrameNanos { }
                        if (shouldFollowBottom && delta > 0) {
                            listState.scrollToItem(
                                messages.size + if (canLoadOlderMessages || isLoadingOlderMessages) 1 else 0
                            )
                        } else {
                            listState.dispatchRawDelta(delta)
                        }
                    }
                }
                previousListHeight = newHeight
            }
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
                imageState = imageState,
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
            if (activeSearchAnchor != null && canLoadNewerMessages) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = { emitIntent(GroupChatUiIntent.LoadNewerMessages) },
                        enabled = !isLoadingNewerMessages
                    ) {
                        Text(stringResource(R.string.content_search_newer))
                    }
                    TextButton(onClick = { emitIntent(GroupChatUiIntent.ReturnToLatest) }) {
                        Text(stringResource(R.string.content_search_latest))
                    }
                }
            }
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
    // 空会话提示占用原有内容区域，保留输入区布局。
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
            AppSurface(
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
    imageState: MessageImageState,
    message: GroupChatMessageItem,
    editing: Boolean,
    editingDraft: String,
    expandedThinkBlockIds: Set<String>,
    onToggleThinkBlock: (String) -> Unit,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    val isUser = message.source == GroupChatMessageSource.User
    val isSystem = message.source == GroupChatMessageSource.System
    // 供应商对助手图片的支持不一致，暂只向用户消息开放新增入口，保留所有消息既有附件的编辑能力。
    val canAddImages = editing && isUser
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
                modifier = Modifier.size(38.dp),
                image = message.avatarImage
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
                if (canAddImages) {
                    Spacer(Modifier.width(6.dp))
                    MessageImageEditButton(imageState) { emitIntent(GroupChatUiIntent.ImageAction(it)) }
                }
            }
            PageSurface(
                kind = PageSurfaceKind.Bubble,
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
                    isUser -> MaterialTheme.colorScheme.primary.copy(alpha = LocalPageBubbleOpacity.current)
                    isSystem -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f * LocalPageBubbleOpacity.current)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBubbleOpacity.current)
                },
                shadowElevation = (if (isUser) 1.5.dp else 0.5.dp) * LocalPageBubbleOpacity.current
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val imageIds = if (editing) imageState.editing else message.imageUuids
                    MessageImageGallery(imageIds, imageState, editing = editing) {
                        emitIntent(GroupChatUiIntent.ImageAction(it))
                    }
                    if (editing) imageState.errorResId?.let { errorResId ->
                        Text(
                            text = stringResource(errorResId),
                            color = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (editing) {
                        GroupMessageEditContent(
                            draft = editingDraft,
                            imageState = imageState,
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
                        AppSurface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f * LocalPageBubbleOpacity.current),
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
    // 按原有分段顺序展示正文和思考内容，展开状态由消息 ID 对应。
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
    // 等待提示保持在流式消息内部，不额外插入列表项。
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
    // 展开状态由页面状态管理，动画只影响思考内容的呈现。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f * LocalPageBubbleOpacity.current),
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
    imageState: MessageImageState,
    isUser: Boolean,
    emitIntent: (GroupChatUiIntent) -> Unit
) {
    // 编辑草稿与图片操作继续由页面状态和意图管理。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpScrollableOutlinedTextField(
            enabled = !imageState.submitting,
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
                    pageUserBubbleContentColor()
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                unfocusedTextColor = if (isUser) {
                    pageUserBubbleContentColor()
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                cursorColor = if (isUser) {
                    pageUserBubbleContentColor()
                } else {
                    MaterialTheme.colorScheme.primary
                },
                focusedBorderColor = if (isUser) {
                    pageUserBubbleContentColor().copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
                unfocusedBorderColor = if (isUser) {
                    pageUserBubbleContentColor().copy(alpha = 0.42f)
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
                enabled = !imageState.submitting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = if (isUser) {
                        pageUserBubbleContentColor().copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { emitIntent(GroupChatUiIntent.SaveEditingMessage) },
                enabled = !imageState.processing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(if (imageState.submitting) R.string.image_loading else R.string.save),
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) {
                        pageUserBubbleContentColor()
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
    // 消息操作沿用原有的生成与编辑限制。
    val iconColor = if (isUser) {
        pageUserBubbleContentColor().copy(alpha = 0.85f)
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
    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isUser) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f * LocalPageBubbleOpacity.current)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f * LocalPageBubbleOpacity.current)
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
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // 点击反馈与禁用状态保持一致，避免不可用操作产生交互。
    val hapticFeedback = LocalHapticFeedback.current
    AppSurface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f * LocalPageBottomBarOpacity.current)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f * LocalPageBottomBarOpacity.current)
        },
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f
                ),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f
                )
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    generating: Boolean,
    imageState: MessageImageState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onImageAction: (MessageImageAction) -> Unit,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onSummarize: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val sendButtonColor by animateColorAsState(
        targetValue = if (generating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "sendButtonColor"
    )

    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        tonalElevation = 5.dp * LocalPageBottomBarOpacity.current,
        shadowElevation = 8.dp * LocalPageBottomBarOpacity.current,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
        ) {
            // 待发送图片抽屉托盘
            DraftAttachmentTray(
                state = imageState,
                enabled = !generating && !imageState.submitting,
                emit = onImageAction
            )

            // 快捷操作胶囊条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.continue_latest_reply),
                    enabled = canContinue && !generating,
                    onClick = onContinue
                )
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.summarize_now),
                    enabled = !generating && !imageState.submitting,
                    onClick = onSummarize
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                RpScrollableOutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp, max = 140.dp),
                    enabled = !generating && !imageState.submitting,
                    placeholder = { Text(stringResource(R.string.group_chat_message_hint)) },
                    shape = RoundedCornerShape(18.dp),
                    maxLines = 5,
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onImageAction(MessageImageAction.Choose(editing = false))
                            },
                            enabled = !generating && !imageState.processing && imageState.canAddDraft
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ImageIcon,
                                contentDescription = stringResource(R.string.attach_images),
                                tint = if (!generating && imageState.canAddDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppSurface(
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
}
