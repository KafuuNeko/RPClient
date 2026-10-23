package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.dialog.MessageImageViewerDialog
import me.kafuuneko.rpclient.ui.message.DraftAttachmentTray
import me.kafuuneko.rpclient.ui.message.MessageImageEditButton
import me.kafuuneko.rpclient.ui.message.MessageImageGallery
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.NarratorAvatarColor
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
import me.kafuuneko.rpclient.ui.widgets.pageUserBubbleContentColor
import androidx.compose.material.icons.rounded.Image as ImageIcon

/**
 * 渲染单聊会话主页，保留消息滚动、输入与生成状态的原有组合位置。
 *
 * @param state 当前会话页面状态
 * @param emit 原有单聊意图发送入口
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatNormalPage(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit,
) {
    val activeSearchAnchor by rememberUpdatedState(state.conversationState.searchAnchorMessageId)
    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    val canLoadOlderMessages by rememberUpdatedState(state.conversationState.canLoadOlderMessages)
    val isLoadingOlderMessages by rememberUpdatedState(state.conversationState.isLoadingOlderMessages)
    // 与列表位置一起恢复，返回设置前的历史阅读位置不应被首次定位或流式跟随覆盖。
    var shouldFollowBottom by rememberSaveable { mutableStateOf(true) }
    var isFirstLoad by rememberSaveable { mutableStateOf(true) }
    var isScrollIndicatorDragged by remember { mutableStateOf(false) }
    var lastTailMessageId by rememberSaveable { mutableStateOf<String?>(value = null) }
    var lastSearchAnchor by rememberSaveable { mutableStateOf<String?>(value = null) }

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

    val currentConversation by rememberUpdatedState(state.conversationState)
    LaunchedEffect(listState) {
        // 等待首次定位和新窗口测量完成，避免把旧布局的底部误认为最新消息。
        snapshotFlow {
            val conversation = currentConversation
            val layout = listState.layoutInfo
            conversation.messages.lastOrNull()?.id?.takeIf {
                !isFirstLoad && conversation.searchAnchorMessageId != null
                    && !conversation.canLoadNewerMessages && !conversation.isLoadingNewerMessages
                    && layout.totalItemsCount == conversation.messages.size + 2
                    && layout.visibleItemsInfo.isNotEmpty() && !listState.canScrollForward
            }
        }.filterNotNull().distinctUntilChanged().collect { messageId ->
            ChatUiIntent.ReachLatestMessage(messageId).emit()
        }
    }

    // 首次定位到底部完成后，用户接近当前窗口顶部才请求更早历史
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to isFirstLoad }
            .distinctUntilChanged()
            .collect { (firstVisibleItemIndex, firstLoad) ->
                val shouldLoadOlderMessages = !firstLoad
                    && firstVisibleItemIndex <= HISTORY_LOAD_THRESHOLD
                    && canLoadOlderMessages
                    && !isLoadingOlderMessages
                if (shouldLoadOlderMessages) {
                    ChatUiIntent.LoadOlderMessages.emit()
                }
            }
    }

    // 只有尾部消息身份改变时才滚到底部，头部加载历史不会打断用户位置
    val tailMessageId = state.conversationState.messages.lastOrNull()?.id
    LaunchedEffect(tailMessageId, activeSearchAnchor) {
        if (activeSearchAnchor != null) {
            // 只在首次进入定位窗口时滚动，继续分页和流式刷新保持阅读位置。
            if (isFirstLoad) {
                val targetIndex = state.conversationState.messages.indexOfFirst { chatMessageUiModel -> chatMessageUiModel.id == activeSearchAnchor }
                if (targetIndex >= 0) listState.scrollToItem(targetIndex + 1)
                isFirstLoad = false
            }
            shouldFollowBottom = false
        } else if (tailMessageId == null) {
            isFirstLoad = true
        } else if (isFirstLoad || tailMessageId != lastTailMessageId || lastSearchAnchor != null) {
            shouldFollowBottom = true
            listState.scrollToItem(state.conversationState.messages.size + 1)
            isFirstLoad = false
        }
        lastTailMessageId = tailMessageId
        lastSearchAnchor = activeSearchAnchor
    }

    // - 内容流式生成或思考块折叠变动时，若处于跟随状态则自动跟随到底部
    LaunchedEffect(
        key1 = state.conversationState.messages.lastOrNull()?.content,
        key2 = state.conversationState.expandedThinkBlockIds,
    ) {
        if (state.conversationState.messages.isEmpty()) return@LaunchedEffect
        val shouldFollowLatestMessage = activeSearchAnchor == null && (isFirstLoad || shouldFollowBottom)
        if (!shouldFollowLatestMessage) return@LaunchedEffect
        listState.scrollToItem(state.conversationState.messages.size + 1)
        isFirstLoad = false
    }

    // 记录列表视口高度，在软键盘弹出/收起导致视口尺寸变化时，将差值转化为滚动偏移，
    // 从而使当前查看的消息被键盘等高顶起，保持位置不变，顶部溢出部分在标题栏下方自然裁切
    val coroutineScope = rememberCoroutineScope()
    var previousListHeight by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        CustomChatTopBar(
            session = state.session,
            character = state.character,
            lorebookState = state.lorebookState,
            generationState = state.conversationState.generationState,
            loadState = state.loadState,
            streamEnabled = state.streamEnabled,
            hasPromptInspection = state.hasPromptInspection,
            hasAvailableProvider = state.hasAvailableProvider,
            sessionLoreDialogVisible = state.dialogState is ChatDialogState.SessionLorebook,
            onBack = { ChatUiIntent.Back.emit() },
            emit = emit,
        )
        if (!state.hasAvailableProvider) {
            NoProviderBanner(onClick = { ChatUiIntent.OpenProviderSettings.emit() })
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .onSizeChanged(
                    onSizeChanged = { size ->
                        val newHeight = size.height
                        val shouldCompensateViewportHeight = activeSearchAnchor == null && previousListHeight > 0 && newHeight > 0 && newHeight != previousListHeight
                        if (shouldCompensateViewportHeight) {
                            val delta = (previousListHeight - newHeight).toFloat()
                            coroutineScope.launch {
                                // 尺寸回调仍处于测量阶段，下一帧再滚动以免重入布局。
                                withFrameNanos { }
                                if (shouldFollowBottom && delta > 0) {
                                    listState.scrollToItem(state.conversationState.messages.size + 1)
                                } else {
                                    listState.dispatchRawDelta(delta)
                                }
                            }
                        }
                        previousListHeight = newHeight
                    },
                )
                .draggableLazyListScrollIndicator(
                    state = listState,
                    onDragStateChanged = { dragging ->
                        isScrollIndicatorDragged = dragging
                        shouldFollowBottom = if (dragging) {
                            false
                        } else {
                            !listState.canScrollForward
                        }
                    },
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.conversationState.canLoadOlderMessages
                || state.conversationState.isLoadingOlderMessages
            ) {
                item(key = "older-messages-loader") {
                    OlderMessagesLoadIndicator(loading = state.conversationState.isLoadingOlderMessages)
                }
            } else {
                item(key = "conversation-start") {
                    ConversationStartHeader(
                        session = state.session,
                        character = state.character,
                        lorebookState = state.lorebookState,
                        streamEnabled = state.streamEnabled,
                        emit = emit,
                    )
                }
            }
            itemsIndexed(
                items = state.conversationState.messages,
                key = { _, message -> message.id },
                contentType = { _, message -> message.role },
            ) { index, message ->
                MessageBubble(
                    imageState = state.imageState,
                    message = message,
                    character = state.character,
                    expandedThinkBlockIds = state.conversationState.expandedThinkBlockIds,
                    editing = message.id == state.conversationState.editingMessageId,
                    editingDraft = state.conversationState.editingMessageDraft,
                    isFirstMessage = !state.conversationState.canLoadOlderMessages
                            && !state.conversationState.isLoadingOlderMessages
                            && index == 0,
                    emit = emit,
                )
            }
            item(key = "conversation-end") {
                if (activeSearchAnchor != null && state.conversationState.canLoadNewerMessages) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TextButton(
                            onClick = { ChatUiIntent.LoadNewerMessages.emit() },
                            enabled = !state.conversationState.isLoadingNewerMessages,
                        ) {
                            Text(stringResource(R.string.content_search_newer))
                        }
                        TextButton(onClick = { ChatUiIntent.ReturnToLatest.emit() }) {
                            Text(stringResource(R.string.content_search_latest))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        if ((state.conversationState.generationState as? ChatGenerationState.Failed)?.canRetryReply == true) {
            TextButton(
                onClick = { ChatUiIntent.RetryImageReply.emit() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.image_retry))
            }
        }
        MessageImageViewerDialog(state = state.imageState, emit = { action -> ChatUiIntent.ImageAction(action).emit() })
        ChatInputBar(
            draft = state.conversationState.inputDraft,
            isGenerating = state.conversationState.generationState.isGenerating(),
            hasAssistantMessage = state.conversationState.hasAssistantMessage,
            imageState = state.imageState,
            emit = emit,
        )
    }
}

/** 当前窗口顶部进入该范围时预取更早消息。 */
private const val HISTORY_LOAD_THRESHOLD = 4

/** 在消息窗口顶部保留稳定高度，并在读取历史时展示轻量进度。 */
@Composable
private fun OlderMessagesLoadIndicator(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun CustomChatTopBar(
    session: ChatSessionItem,
    character: ChatCharacterItem,
    lorebookState: ChatLorebookState,
    generationState: ChatGenerationState,
    loadState: ChatLoadState,
    streamEnabled: Boolean,
    hasPromptInspection: Boolean,
    hasAvailableProvider: Boolean = true,
    sessionLoreDialogVisible: Boolean,
    onBack: () -> Unit,
    emit: ChatUiIntent.() -> Unit,
) {
    // 标题栏沿用会话的生成状态与操作回调，设置入口只发送页面切换意图。
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
                .windowInsetsPadding(insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

            AvatarPreview(
                avatarText = character.avatarText,
                avatarColor = character.accentColor,
                image = character.avatarImage,
                size = 36,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(session.title)
                        val status = chatStatusText(
                            loadState = loadState,
                            generationState = generationState,
                            streamEnabled = streamEnabled,
                            hasAvailableProvider = hasAvailableProvider,
                        )
                        if (status.isNotBlank()) {
                            append(" • ")
                            append(status)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = { ChatUiIntent.OpenPromptInspector.emit() },
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
                onClick = { ChatUiIntent.ShowSessionLoreDialog.emit() },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Book,
                    contentDescription = stringResource(R.string.session_world_book),
                    modifier = Modifier.size(20.dp),
                    tint = if (sessionLoreDialogVisible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            IconButton(
                onClick = { ChatUiIntent.OpenChatSettings.emit() },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.generation_params),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun ConversationStartHeader(
    session: ChatSessionItem,
    character: ChatCharacterItem,
    lorebookState: ChatLorebookState,
    streamEnabled: Boolean,
    emit: ChatUiIntent.() -> Unit,
    modifier: Modifier = Modifier,
) {
    // 介绍卡和消息共用内容区背景，状态标签单独保证前景信息的可读性。
    val enabledLorebookCount = lorebookState.groups.sumOf { it.enabledCount }
    PageSurface(
        kind = PageSurfaceKind.Bubble,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f * LocalPageBubbleOpacity.current),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AvatarPreview(
                avatarText = character.avatarText,
                avatarColor = character.accentColor,
                image = character.avatarImage,
                size = 68,
            )

            Text(
                text = character.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (character.description.isNotBlank()) {
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            ConversationMetadata(
                messageCount = session.messageCount,
                enabledLorebookCount = enabledLorebookCount,
                streamEnabled = streamEnabled,
            )
            TextButton(
                onClick = { ChatUiIntent.OpenCharacterEditor.emit() },
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.edit_character_title))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    imageState: MessageImageState,
    message: ChatMessageUiModel,
    character: ChatCharacterItem,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    val isUser = message.role == MessageRole.User
    // 供应商对助手图片的支持不一致，暂只向用户消息开放新增入口，保留所有消息既有附件的编辑能力。
    val canAddImages = editing && isUser
    val imageIds = if (editing) imageState.editing else message.imageUuids
    val hasImageHeader = imageIds.isNotEmpty() || message.imageUuids.isNotEmpty() || canAddImages
    var showActions by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AvatarPreview(
                avatarText = if (message.speaker == character.name) {
                    character.avatarText
                } else {
                    message.speaker.take(1).uppercase()
                },
                avatarColor = if (message.speaker == character.name) character.accentColor else NarratorAvatarColor,
                image = if (message.speaker == character.name) character.avatarImage else null,
                size = 34,
                modifier = Modifier.padding(end = 8.dp, top = 2.dp),
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val bubbleShape = when (message.role) {
                MessageRole.User -> RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp,
                )

                MessageRole.Assistant -> RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 18.dp,
                )

                MessageRole.Narrator -> RoundedCornerShape(14.dp)
            }
            val bubbleColor = when (message.role) {
                MessageRole.User -> MaterialTheme.colorScheme.primary.copy(alpha = LocalPageBubbleOpacity.current)
                MessageRole.Assistant -> MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBubbleOpacity.current)
                MessageRole.Narrator -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f * LocalPageBubbleOpacity.current)
            }
            PageSurface(
                kind = PageSurfaceKind.Bubble,
                modifier = Modifier
                    .widthIn(max = if (isUser) 310.dp else 295.dp)
                    .clickable(enabled = !editing, onClick = { showActions = !showActions }),
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = (if (isUser) 1.5.dp else 0.5.dp) * LocalPageBubbleOpacity.current,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        // 图片消息为标题预留同样高度，添加按钮出现时不推动下方网格。
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (hasImageHeader) 32.dp else 0.dp),
                    ) {
                        Text(
                            text = message.speaker,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.72f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                        if (canAddImages) {
                            Spacer(Modifier.width(4.dp))
                            MessageImageEditButton(
                                state = imageState,
                                emit = { action ->
                                    ChatUiIntent.ImageAction(action).emit()
                                },
                            )
                        }
                    }
                    MessageImageGallery(
                        ids = imageIds,
                        state = imageState,
                        editing = editing,
                        emit = { action ->
                            ChatUiIntent.ImageAction(action).emit()
                        },
                    )
                    if (editing) imageState.errorResId?.let { errorResId ->
                        Text(
                            text = stringResource(errorResId),
                            color = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (editing) {
                        MessageEditContent(
                            draft = editingDraft,
                            imageState = imageState,
                            isUser = isUser,
                            emit = emit,
                        )
                    } else {
                        MessageContent(
                            message = message,
                            expandedThinkBlockIds = expandedThinkBlockIds,
                            isUser = isUser,
                            emit = emit,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showActions && !editing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                PageSurface(
                    kind = PageSurfaceKind.Bubble,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f * LocalPageBubbleOpacity.current),
                    shadowElevation = 2.dp * LocalPageBubbleOpacity.current,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MessageActions(message = message, isFirstMessage = isFirstMessage, emit = emit)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: ChatMessageUiModel,
    expandedThinkBlockIds: Set<String>,
    isUser: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 按现有消息分段顺序展示文本与思考内容，展开状态仍由页面状态控制。
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.isStreaming && (message.content.isBlank() || message.parts.isEmpty())) {
            StreamingStatus(
                text = stringResource(R.string.waiting_for_response),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f),
            )
        }
        message.parts.forEach { part ->
            when (part) {
                is MessageContentPart.Text -> {
                    if (part.content.isNotBlank()) {
                        MarkdownMessageText(
                            content = part.content,
                            isUser = isUser,
                        )
                    }
                }

                is MessageContentPart.Think -> ThinkBlock(
                    part = part,
                    expanded = part.id in expandedThinkBlockIds,
                    isThinking = message.isStreaming && !part.isComplete,
                    emit = emit,
                )
            }
        }
    }
}

@Composable
private fun StreamingStatus(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // 流式等待提示保持在消息内部，避免影响消息列表的结构。
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun MessageEditContent(
    draft: String,
    imageState: MessageImageState,
    isUser: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 编辑框沿用正文排版，短消息仅占一行，长消息在高度上限内滚动。
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RpScrollableOutlinedTextField(
            enabled = !imageState.submitting,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            value = draft,
            onValueChange = { text -> ChatUiIntent.ChangeEditingMessageDraft(text).emit() },
            minLines = 1,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.onSurface,
                cursorColor = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.primary,
                focusedContainerColor = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.06f * LocalPageBubbleOpacity.current) else Color.Transparent,
                unfocusedContainerColor = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.04f * LocalPageBubbleOpacity.current) else Color.Transparent,
                focusedBorderColor = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { ChatUiIntent.CancelEditingMessage.emit() },
                enabled = !imageState.submitting,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    color = if (isUser) pageUserBubbleContentColor().copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Normal,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { ChatUiIntent.SaveEditingMessage.emit() },
                enabled = !imageState.processing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(if (imageState.submitting) R.string.image_loading else R.string.save),
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) pageUserBubbleContentColor() else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ThinkBlock(
    part: MessageContentPart.Think,
    expanded: Boolean,
    isThinking: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 展开操作仍交由页面状态管理，局部动画仅负责呈现。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { ChatUiIntent.ToggleThinkBlock(part.id).emit() }),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = (if (isThinking) 0.55f else 0.38f) * LocalPageBubbleOpacity.current),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                if (isThinking) {
                    StreamingStatus(
                        text = stringResource(R.string.thinking),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.thought_process),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) stringResource(R.string.hide) else stringResource(R.string.show),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Text(
                    text = part.content,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 保留禁用态和点击反馈，避免生成期间误触不可用操作。
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
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f,
                ),
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f,
                ),
            )
        }
    }
}

@Composable
private fun MessageActions(
    message: ChatMessageUiModel,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 编辑与生成操作沿用消息自身的可用性限制。
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

        IconButton(
            onClick = { ChatUiIntent.CopyMessage(message.id).emit() },
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                modifier = Modifier.size(17.dp),
                tint = iconColor,
            )
        }
        IconButton(
            onClick = { ChatUiIntent.StartEditMessage(message.id).emit() },
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier.size(17.dp),
                tint = iconColor,
            )
        }
        if (!isFirstMessage) {
            IconButton(
                onClick = { ChatUiIntent.BranchFromMessage(message.id).emit() },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ForkRight,
                    contentDescription = stringResource(R.string.branch_from_message),
                    modifier = Modifier.size(17.dp),
                    tint = iconColor,
                )
            }
            if (message.role == MessageRole.Assistant) {
                IconButton(
                    onClick = { ChatUiIntent.RegenerateFromMessage(message.id).emit() },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.regenerate),
                        modifier = Modifier.size(17.dp),
                        tint = iconColor,
                    )
                }
            }
            IconButton(
                onClick = { ChatUiIntent.DeleteMessageClick(message.id).emit() },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(17.dp),
                    tint = iconColor,
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    isGenerating: Boolean,
    hasAssistantMessage: Boolean,
    imageState: MessageImageState,
    emit: ChatUiIntent.() -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val sendButtonColor by animateColorAsState(
        targetValue = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "sendButtonColor",
    )

    PageSurface(
        kind = PageSurfaceKind.BottomBar,
        color = MaterialTheme.colorScheme.surface.copy(alpha = LocalPageBottomBarOpacity.current),
        tonalElevation = 2.dp * LocalPageBottomBarOpacity.current,
        shadowElevation = 3.dp * LocalPageBottomBarOpacity.current,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                    .height(0.5.dp),
            )

            // 附件为空时仍保留选图失败提示。
            DraftAttachmentTray(
                state = imageState,
                enabled = !isGenerating && !imageState.submitting,
                emit = { action -> ChatUiIntent.ImageAction(action).emit() },
            )

            // - 快捷操作胶囊条（随页面整体上移平推，常驻方便快速操作）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.continue_latest_reply),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.ContinueLast.emit() },
                )
                QuickActionPill(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.impersonate_user),
                    enabled = !isGenerating && !imageState.submitting,
                    onClick = { ChatUiIntent.ImpersonateUser.emit() },
                )
                QuickActionPill(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.regenerate_latest_reply),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.RegenerateLast.emit() },
                )
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.summarize_now),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.SummarizeNow.emit() },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                RpScrollableOutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = draft,
                    onValueChange = { text -> ChatUiIntent.ChangeInputDraft(text).emit() },
                    enabled = !isGenerating && !imageState.submitting,
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        IconButton(
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ChatUiIntent.ImageAction(MessageImageAction.Choose(editing = false))
                                    .emit()
                            },
                            enabled = !isGenerating && !imageState.processing && imageState.canAddDraft,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ImageIcon,
                                contentDescription = stringResource(R.string.attach_images),
                                tint = if (!isGenerating && imageState.canAddDraft) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            )
                        }
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.input_next_story),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            ),
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f * LocalPageBottomBarOpacity.current),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.22f * LocalPageBottomBarOpacity.current,
                        ),
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium,
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
                        if (isGenerating) ChatUiIntent.StopGeneration.emit()
                        else ChatUiIntent.SendMessage.emit()
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (isGenerating) stringResource(R.string.stop) else stringResource(
                                id = R.string.send,
                            ),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun ChatGenerationState.isGenerating(): Boolean {
    return this is ChatGenerationState.Requesting || this is ChatGenerationState.Streaming
}

@Composable
private fun ChatGenerationState.label(streamEnabled: Boolean): String {
    return when (this) {
        ChatGenerationState.Idle -> if (streamEnabled) stringResource(R.string.connected_streaming_enabled) else stringResource(
            id = R.string.connected,
        )

        ChatGenerationState.Requesting -> stringResource(R.string.requesting_model)
        is ChatGenerationState.Streaming -> stringResource(R.string.generating)
        is ChatGenerationState.Failed -> message
    }
}

@Composable
private fun chatStatusText(
    loadState: ChatLoadState,
    generationState: ChatGenerationState,
    streamEnabled: Boolean,
    hasAvailableProvider: Boolean = true,
): String {
    return if (loadState == ChatLoadState.Saving) {
        stringResource(R.string.updating_summary)
    } else if (!hasAvailableProvider && generationState == ChatGenerationState.Idle) {
        stringResource(R.string.no_model_configured)
    } else {
        generationState.label(streamEnabled)
    }
}

@Composable
private fun AvatarPreview(
    avatarText: String,
    avatarColor: Color,
    image: ImageBitmap?,
    size: Int,
    modifier: Modifier = Modifier,
) {
    // 有图片时沿用裁切形状，没有图片时使用原有文字头像。
    val cornerRadius = remember(size) { (size * 0.28).coerceAtLeast(8.0).dp }
    AppSurface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = avatarColor.copy(alpha = 0.14f),
        border = BorderStroke(width = 0.5.dp, color = avatarColor.copy(alpha = 0.30f)),
    ) {
        if (image == null) {
            RpAvatar(
                text = avatarText,
                color = avatarColor,
                modifier = Modifier.size(size.dp),
                shape = RoundedCornerShape(cornerRadius),
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** 同屏对照普通态与编辑态，便于检查图片位置和短文本编辑高度。 */
@Preview(name = "图片消息 · 普通与编辑", widthDp = 360, heightDp = 700)
@Composable
private fun PreviewImageMessageEditing() {
    PreviewImageMessages(listOf(3 to false, 3 to true))
}

/** 覆盖单图、双图、四图与折叠多图，检查窄屏下的裁切和布局。 */
@Preview(name = "图片消息 · 数量适配", widthDp = 320, heightDp = 1100)
@Composable
private fun PreviewImageMessageCounts() {
    PreviewImageMessages(listOf(1 to false, 2 to false, 4 to true, 6 to false))
}

@Composable
private fun PreviewImageMessages(examples: List<Pair<Int, Boolean>>) {
    // 合成横向缩略图只供预览，覆盖裁切边界而不依赖私有图片文件或异步加载。
    val thumbnails = remember {
        (0..5).associate { index ->
            val bitmap = ImageBitmap(width = 240, height = 160)
            val canvas = Canvas(bitmap)
            canvas.drawRect(
                left = 0f,
                top = 0f,
                right = 240f,
                bottom = 160f,
                paint = Paint().apply { color = Color(0xFF203D50) },
            )
            canvas.drawCircle(
                center = Offset(x = 120f, y = 60f),
                radius = 32f,
                paint = Paint().apply { color = Color(0xFFDBAD72) },
            )
            canvas.drawRect(
                left = 0f,
                top = 105f,
                right = 240f,
                bottom = 160f,
                paint = Paint().apply { color = Color(0xFF47706C) },
            )
            index.toString() to bitmap
        }
    }
    val character = ChatCharacterItem(
        id = 1,
        name = "Preview",
        description = "",
        personality = "",
        scenario = "",
        examplesOfDialogue = "",
        postHistoryInstructions = "",
        creatorNotes = "",
        avatarText = "P",
        accentColor = Color.Blue,
    )
    AppTheme(darkTheme = true, dynamicColor = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            examples.forEachIndexed { index, (count, editing) ->
                val ids = thumbnails.keys.take(count)
                MessageBubble(
                    imageState = MessageImageState(
                        editing = ids,
                        thumbnails = thumbnails,
                        canAddEditing = count < 4,
                    ),
                    message = ChatMessageUiModel(
                        id = index.toString(),
                        role = MessageRole.User,
                        speaker = "You",
                        content = "还有很多呢",
                        parts = listOf(MessageContentPart.Text("还有很多呢")),
                        time = "19:28",
                        tokenCount = 0,
                        imageUuids = ids,
                    ),
                    character = character,
                    expandedThinkBlockIds = emptySet(),
                    editing = editing,
                    editingDraft = "还有很多呢",
                    isFirstMessage = false,
                    emit = {},
                )
            }
        }
    }
}
