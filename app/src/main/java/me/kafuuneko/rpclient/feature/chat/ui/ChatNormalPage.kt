package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import me.kafuuneko.rpclient.libs.media.MessageImageState
import me.kafuuneko.rpclient.model.ChatWritingState
import me.kafuuneko.rpclient.model.MessageContentPart
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
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.NarratorAvatarColor
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.LocalPageTopBarOpacity
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.utils.toPreview

/**
 * 渲染单聊会话主页，保留消息滚动、输入与生成状态的原有组合位置。
 *
 * @param state 当前会话页面状态
 * @param emit 原有单聊意图发送入口
 */
@Composable
internal fun ChatNormalPage(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit,
) {
    val conversation = state.conversationState

    // 顶栏、共享消息列表与底栏按纵向堆叠，各自负责对应区域的安全区与背景透明度。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        CustomChatTopBar(
            session = state.session,
            character = state.character,
            generationState = conversation.generationState,
            loadState = state.loadState,
            streamEnabled = state.streamEnabled,
            hasPromptInspection = state.hasPromptInspection,
            hasAvailableProvider = state.hasAvailableProvider,
            settingsEnabled = !conversation.generationState.isGenerating() &&
                state.dialogState != ChatDialogState.Summarizing,
            sessionLoreDialogVisible = state.dialogState is ChatDialogState.SessionLorebook,
            onBack = { ChatUiIntent.Back.emit() },
            emit = emit,
        )
        if (!state.hasAvailableProvider) {
            NoProviderBanner(onClick = { ChatUiIntent.OpenProviderSettings.emit() })
        }
        ConversationMessageList(
            messages = conversation.messages,
            itemId = { message -> message.id },
            itemContentType = { message -> message.role },
            itemContentKey = { message -> message.content },
            canLoadOlderMessages = conversation.canLoadOlderMessages,
            historyLoadState = conversation.historyLoadState,
            searchAnchorMessageId = conversation.searchAnchorMessageId,
            canLoadNewerMessages = conversation.canLoadNewerMessages,
            expandedThinkBlockIds = conversation.expandedThinkBlockIds,
            onLoadOlderMessages = { ChatUiIntent.LoadOlderMessages.emit() },
            onLoadNewerMessages = { ChatUiIntent.LoadNewerMessages.emit() },
            onReturnToLatest = { ChatUiIntent.ReturnToLatest.emit() },
            onReachLatestMessage = { message ->
                ChatUiIntent.ReachLatestMessage(message.id).emit()
            },
            modifier = Modifier.weight(1f),
            startHeader = {
                ConversationStartHeader(
                    session = state.session,
                    character = state.character,
                    lorebookState = state.lorebookState,
                    streamEnabled = state.streamEnabled,
                    emit = emit,
                )
            },
            messageContent = { index, message ->
                ChatMessageBubble(
                    imageState = state.imageState,
                    message = message,
                    character = state.character,
                    expandedThinkBlockIds = conversation.expandedThinkBlockIds,
                    editing = message.id == conversation.editingMessageId,
                    editingDraft = conversation.editingMessageDraft,
                    isFirstMessage = !conversation.canLoadOlderMessages
                        && !conversation.historyLoadState.isLoadingOlder
                        && index == 0,
                    emit = emit,
                )
            },
        )
        MessageImageViewerDialog(
            state = state.imageState,
            emit = { action -> ChatUiIntent.ImageAction(action).emit() },
        )
        ChatInputBar(
            draft = conversation.inputDraft,
            writingState = conversation.writingState,
            isGenerating = conversation.generationState.isGenerating(),
            hasAssistantMessage = conversation.hasAssistantMessage,
            canRegenerateLatestReply = conversation.canRegenerateLatestReply,
            imageState = state.imageState,
            emit = emit,
        )
    }
}

@Composable
private fun CustomChatTopBar(
    session: ChatSessionItem,
    character: ChatCharacterItem,
    generationState: ChatGenerationState,
    loadState: ChatLoadState,
    streamEnabled: Boolean,
    hasPromptInspection: Boolean,
    hasAvailableProvider: Boolean = true,
    settingsEnabled: Boolean,
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
                .windowInsetsPadding(
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                )
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
                    tint = if (sessionLoreDialogVisible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    },
                )
            }
            IconButton(
                onClick = { ChatUiIntent.OpenChatSettings.emit() },
                enabled = settingsEnabled,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.generation_params),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (settingsEnabled) 0.72f else 0.3f),
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
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.30f * LocalPageBubbleOpacity.current,
        ),
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
                // 介绍卡只展示三行，避免为完整角色设定执行段落测量。
                val descriptionPreview = remember(character.description) {
                    character.description.toPreview(maxLength = 1024)
                }
                Text(
                    text = descriptionPreview,
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

/** 将单聊消息模型与意图回调接入公共消息气泡。 */
@Composable
private fun ChatMessageBubble(
    imageState: MessageImageState,
    message: ChatMessageUiModel,
    character: ChatCharacterItem,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    val item = remember(message, character) {
        message.toConversationMessageItem(character = character)
    }
    // 单聊消息复用公共气泡布局，并通过操作栏槽位保留分叉与首条限制规则。
    ConversationMessageBubble(
        message = item,
        imageState = imageState,
        expandedThinkBlockIds = expandedThinkBlockIds,
        editing = editing,
        editingDraft = editingDraft,
        onToggleThinkBlock = { blockId -> ChatUiIntent.ToggleThinkBlock(blockId).emit() },
        onEditingDraftChange = { text -> ChatUiIntent.ChangeEditingMessageDraft(text).emit() },
        onSaveEdit = { ChatUiIntent.SaveEditingMessage.emit() },
        onCancelEdit = { ChatUiIntent.CancelEditingMessage.emit() },
        onImageAction = { action -> ChatUiIntent.ImageAction(action).emit() },
        actions = {
            ChatMessageActions(
                message = message,
                isFirstMessage = isFirstMessage,
                emit = emit,
            )
        },
    )
}

/** 将单聊消息模型映射为公共消息气泡展示模型。 */
private fun ChatMessageUiModel.toConversationMessageItem(
    character: ChatCharacterItem,
): ConversationMessageItem {
    val visualRole = when (role) {
        MessageRole.User -> ConversationMessageRole.User
        MessageRole.Assistant -> ConversationMessageRole.Assistant
        MessageRole.Narrator -> ConversationMessageRole.Narrator
    }
    // 用户消息不展示头像；非用户消息匹配当前角色时使用角色头像，否则回退旁白样式。
    val avatar = if (role == MessageRole.User) {
        null
    } else {
        val isCurrentCharacter = speaker == character.name
        ConversationMessageAvatar(
            text = if (isCurrentCharacter) {
                character.avatarText
            } else {
                speaker.firstOrNull()?.uppercase() ?: "?"
            },
            accentColor = if (isCurrentCharacter) character.accentColor else NarratorAvatarColor,
            image = if (isCurrentCharacter) character.avatarImage else null,
        )
    }
    return ConversationMessageItem(
        id = id,
        role = visualRole,
        speaker = speaker,
        content = content,
        parts = parts,
        time = time,
        imageUuids = imageUuids,
        isStreaming = isStreaming,
        avatar = avatar,
    )
}

/** 提供单聊消息悬浮操作栏中的业务按钮，首条开场白不开放分叉、重生成与删除。 */
@Composable
private fun RowScope.ChatMessageActions(
    message: ChatMessageUiModel,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 全部消息均支持复制与编辑。
    ConversationMessageActionButton(
        icon = Icons.Rounded.ContentCopy,
        contentDescription = stringResource(R.string.copy),
        onClick = { ChatUiIntent.CopyMessage(message.id).emit() },
    )
    ConversationMessageActionButton(
        icon = Icons.Rounded.Edit,
        contentDescription = stringResource(R.string.edit),
        onClick = { ChatUiIntent.StartEditMessage(message.id).emit() },
    )
    if (isFirstMessage) return
    // 非首条消息开放分支、角色回复重生成与删除操作。
    ConversationMessageActionButton(
        icon = Icons.Rounded.ForkRight,
        contentDescription = stringResource(R.string.branch_from_message),
        onClick = { ChatUiIntent.BranchFromMessage(message.id).emit() },
    )
    if (message.role == MessageRole.Assistant) {
        ConversationMessageActionButton(
            icon = Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.regenerate),
            onClick = { ChatUiIntent.RegenerateFromMessage(message.id).emit() },
        )
    }
    ConversationMessageActionButton(
        icon = Icons.Rounded.Delete,
        contentDescription = stringResource(R.string.delete),
        onClick = { ChatUiIntent.DeleteMessageClick(message.id).emit() },
    )
}

/** 单聊输入意图与代写面板接入公共输入区。 */
@Composable
private fun ChatInputBar(
    draft: String,
    writingState: ChatWritingState?,
    isGenerating: Boolean,
    hasAssistantMessage: Boolean,
    canRegenerateLatestReply: Boolean,
    imageState: MessageImageState,
    emit: ChatUiIntent.() -> Unit,
) {
    // 代写期间保留输入区和选择能力，具体状态继续由单聊页面管理。
    ConversationComposer(
        draft = draft,
        placeholder = stringResource(R.string.input_next_story),
        imageState = imageState,
        isGenerating = isGenerating,
        inputEnabled = !isGenerating || writingState != null,
        readOnly = writingState != null,
        onDraftChange = { ChatUiIntent.ChangeInputDraft(it).emit() },
        onSend = { ChatUiIntent.SendMessage.emit() },
        onStop = { ChatUiIntent.StopGeneration.emit() },
        onImageAction = { ChatUiIntent.ImageAction(it).emit() },
        beforeActions = {
            writingState?.let {
                ChatWritingPanel(
                    state = it,
                    onToggleReasoning = { ChatUiIntent.ToggleWritingReasoning.emit() },
                )
            }
        },
        actions = {
            ConversationQuickAction(
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.continue_latest_reply),
                enabled = hasAssistantMessage && !isGenerating,
                onClick = { ChatUiIntent.ContinueLast.emit() },
            )
            ConversationQuickAction(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.impersonate_user),
                enabled = !isGenerating && !imageState.loadState.isSubmitting,
                onClick = { ChatUiIntent.ImpersonateUser.emit() },
            )
            ConversationQuickAction(
                icon = Icons.Rounded.Refresh,
                label = stringResource(R.string.regenerate_latest_reply),
                enabled = canRegenerateLatestReply && !isGenerating,
                onClick = { ChatUiIntent.RegenerateLast.emit() },
            )
            ConversationQuickAction(
                icon = Icons.Rounded.AutoAwesome,
                label = stringResource(R.string.summarize_now),
                enabled = hasAssistantMessage && !isGenerating,
                onClick = { ChatUiIntent.SummarizeNow.emit() },
            )
        },
    )
}

private fun ChatGenerationState.isGenerating(): Boolean {
    return this is ChatGenerationState.Requesting || this is ChatGenerationState.Streaming
}

@Composable
private fun ChatGenerationState.label(streamEnabled: Boolean): String {
    return when (this) {
        ChatGenerationState.Idle -> if (streamEnabled) {
            stringResource(R.string.connected_streaming_enabled)
        } else {
            stringResource(id = R.string.connected)
        }

        ChatGenerationState.Requesting -> stringResource(R.string.requesting_model)
        is ChatGenerationState.Streaming -> stringResource(R.string.generating)
        is ChatGenerationState.Failed -> stringResource(R.string.generation_failed)
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
                ChatMessageBubble(
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
