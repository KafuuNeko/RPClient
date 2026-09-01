package me.kafuuneko.rpclient.feature.chat.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatConversationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.utils.toggle
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.widgets.MarkdownMessageText
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.DefaultCharacterAccentColor
import me.kafuuneko.rpclient.ui.theme.NarratorAvatarColor
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator
import me.kafuuneko.rpclient.ui.widgets.NoProviderBanner
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 单角色聊天页 Compose 入口，根据页面状态切换会话区与设置区。 */
@Composable
fun ChatLayout(
    uiState: ChatUiState,
    emit: ChatUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is ChatUiState.Normal) { ChatUiIntent.Back.emit() }
    when (uiState) {
        ChatUiState.None -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
        }

        is ChatUiState.Finished -> ChatLayout(uiState.previous) {}

        is ChatUiState.Normal -> {
            when (uiState.page) {
                ChatPage.Conversation -> ChatNormal(uiState, emit)
                ChatPage.Settings -> ChatSettingsPage(
                    session = uiState.session,
                    lorebookState = uiState.lorebookState,
                    loadState = uiState.loadState,
                    emit = emit
                )
            }
            DialogSwitch(uiState, emit)
        }
    }
}

@Composable
private fun ChatNormal(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit
) {
    val listState = rememberLazyListState()
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()
    var shouldFollowBottom by remember { mutableStateOf(true) }
    var isFirstLoad by remember { mutableStateOf(true) }
    var isScrollIndicatorDragged by remember { mutableStateOf(false) }

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

    // - 只要收到新消息或发送消息，立即恢复底部跟随并平滑滚动到末尾
    LaunchedEffect(state.conversationState.messages.size) {
        if (state.conversationState.messages.isNotEmpty()) {
            shouldFollowBottom = true
            listState.scrollToItem(state.conversationState.messages.size + 1)
            isFirstLoad = false
        }
    }

    // - 内容流式生成或思考块折叠变动时，若处于跟随状态则自动跟随到底部
    LaunchedEffect(
        state.conversationState.messages.lastOrNull()?.content,
        state.conversationState.expandedThinkBlockIds
    ) {
        if (state.conversationState.messages.isNotEmpty()) {
            if (isFirstLoad || shouldFollowBottom) {
                listState.scrollToItem(state.conversationState.messages.size + 1)
                isFirstLoad = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
            emit = emit
        )
        if (!state.hasAvailableProvider) {
            NoProviderBanner(
                onClick = { ChatUiIntent.OpenProviderSettings.emit() }
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "conversation-start") {
                ConversationStartHeader(
                    session = state.session,
                    character = state.character,
                    lorebookState = state.lorebookState,
                    streamEnabled = state.streamEnabled,
                    emit = emit
                )
            }
            itemsIndexed(
                items = state.conversationState.messages,
                key = { _, message -> message.id },
                contentType = { _, message -> message.role }
            ) { index, message ->
                MessageBubble(
                    message = message,
                    character = state.character,
                    expandedThinkBlockIds = state.conversationState.expandedThinkBlockIds,
                    editing = message.id == state.conversationState.editingMessageId,
                    editingDraft = state.conversationState.editingMessageDraft,
                    isFirstMessage = index == 0,
                    emit = emit
                )
            }
            item(key = "conversation-end") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        ChatInputBar(
            draft = state.conversationState.inputDraft,
            isGenerating = state.conversationState.generationState.isGenerating(),
            hasAssistantMessage = state.conversationState.messages.any {
                it.role == MessageRole.Assistant
            },
            emit = emit
        )
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
    emit: ChatUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

            AvatarPreview(
                avatarText = character.avatarText,
                avatarColor = character.accentColor,
                image = character.avatarImage,
                size = 36
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(session.title)
                        val status = chatStatusText(
                            loadState,
                            generationState,
                            streamEnabled,
                            hasAvailableProvider
                        )
                        if (status.isNotBlank()) {
                            append(" • ")
                            append(status)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = { ChatUiIntent.OpenPromptInspector.emit() },
                enabled = hasPromptInspection,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = stringResource(R.string.prompt_inspector_title),
                    modifier = Modifier.size(20.dp),
                    tint = if (hasPromptInspection) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    }
                )
            }
            IconButton(
                onClick = { ChatUiIntent.ShowSessionLoreDialog.emit() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Book,
                    contentDescription = stringResource(R.string.session_world_book),
                    modifier = Modifier.size(20.dp),
                    tint = if (sessionLoreDialogVisible) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
            IconButton(
                onClick = { ChatUiIntent.OpenChatSettings.emit() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.generation_params),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
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
    modifier: Modifier = Modifier
) {
    val enabledLorebookCount = lorebookState.groups.sumOf { it.enabledCount }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AvatarPreview(
                avatarText = character.avatarText,
                avatarColor = character.accentColor,
                image = character.avatarImage,
                size = 68
            )

            Text(
                text = character.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (character.description.isNotBlank()) {
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RpMetaPill(stringResource(R.string.messages_count, session.messageCount))
                RpMetaPill(stringResource(R.string.world_books_enabled, enabledLorebookCount))
                RpMetaPill(
                    if (streamEnabled) stringResource(R.string.streaming_on)
                    else stringResource(R.string.streaming_off)
                )
            }
            OutlinedButton(
                onClick = { ChatUiIntent.OpenCharacterEditor.emit() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.edit_character_title))
            }
        }
    }
}

@Composable
private fun SessionLoreGroup(
    group: ChatLorebookGroupItem,
    expanded: Boolean,
    onExpandedChange: () -> Unit,
    emit: ChatUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            RpIconBubble(Icons.Rounded.Book)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.lorebookName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(
                        R.string.enabled_entries_count,
                        group.enabledCount,
                        group.totalCount
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
                )
            }
            Switch(
                checked = group.isAllEnabled,
                onCheckedChange = { ChatUiIntent.ToggleSessionLorebook(group.lorebookId).emit() }
            )
        }
        if (expanded) {
            group.entries.forEach { entry ->
                SessionLoreEntryRow(entry, emit)
            }
        }
    }
}

@Composable
private fun SessionLoreEntryRow(
    entry: ChatLorebookEntryItem,
    emit: ChatUiIntent.() -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(Icons.Rounded.Book)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                entry.lorebookName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
            )
            RpTagRow(
                tags = entry.displayTags(stringResource(R.string.entry_constant)),
                maxCount = 2
            )
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = { ChatUiIntent.ToggleSessionLoreEntry(entry.id).emit() }
        )
    }
}

private fun ChatLorebookEntryItem.displayTags(constantLabel: String): List<String> {
    return buildList {
        if (constant) add(constantLabel)
        addAll(keywords)
    }
}

@Composable
private fun LorebookSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = stringResource(R.string.search_world_books),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessageUiModel,
    character: ChatCharacterItem,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    val isUser = message.role == MessageRole.User
    var showActions by remember(message.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
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
                modifier = Modifier.padding(end = 8.dp, top = 2.dp)
            )
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (isUser) 310.dp else 295.dp)
                    .clickable {
                        if (!editing) {
                            showActions = !showActions
                        }
                    },
                shape = when (message.role) {
                    MessageRole.User -> RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 4.dp
                    )

                    MessageRole.Assistant -> RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 4.dp,
                        bottomEnd = 18.dp
                    )

                    MessageRole.Narrator -> RoundedCornerShape(14.dp)
                },
                color = when (message.role) {
                    MessageRole.User -> MaterialTheme.colorScheme.primary
                    MessageRole.Assistant -> MaterialTheme.colorScheme.surface
                    MessageRole.Narrator -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                },
                border = when (message.role) {
                    MessageRole.User -> BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                    MessageRole.Assistant -> BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                    )

                    MessageRole.Narrator -> BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    )
                },
                shadowElevation = if (isUser) 1.5.dp else 0.5.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.speaker,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.time,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    if (editing) {
                        MessageEditContent(
                            draft = editingDraft,
                            isUser = isUser,
                            emit = emit
                        )
                    } else {
                        MessageContent(
                            message = message,
                            expandedThinkBlockIds = expandedThinkBlockIds,
                            isUser = isUser,
                            emit = emit
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showActions && !editing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageActions(message, isFirstMessage, emit)
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
    emit: ChatUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (message.isStreaming && (message.content.isBlank() || message.parts.isEmpty())) {
            StreamingStatus(
                text = stringResource(R.string.waiting_for_response),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.64f)
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

                is MessageContentPart.Think -> ThinkBlock(
                    part = part,
                    expanded = part.id in expandedThinkBlockIds,
                    isThinking = message.isStreaming && !part.isComplete,
                    emit = emit
                )
            }
        }
    }
}

@Composable
private fun StreamingStatus(
    text: String,
    color: Color,
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
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun MessageEditContent(
    draft: String,
    isUser: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft,
            onValueChange = { ChatUiIntent.ChangeEditingMessageDraft(it).emit() },
            minLines = 1,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                cursorColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                focusedBorderColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f) else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { ChatUiIntent.CancelEditingMessage.emit() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.cancel),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = { ChatUiIntent.SaveEditingMessage.emit() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(R.string.save),
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
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
    emit: ChatUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { ChatUiIntent.ToggleThinkBlock(part.id).emit() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isThinking) 0.55f else 0.38f),
        border = BorderStroke(
            0.8.dp,
            if (isThinking) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = if (isThinking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (isThinking) {
                    StreamingStatus(
                        text = stringResource(R.string.thinking),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        stringResource(R.string.thought_process),
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
                    part.content,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
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
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        },
        border = BorderStroke(
            0.5.dp,
            if (enabled) {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)
            }
        ),
        modifier = Modifier.clickable(
            enabled = enabled,
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f
                ),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.38f
                )
            )
        }
    }
}

@Composable
private fun MessageActions(
    message: ChatMessageUiModel,
    isFirstMessage: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

        IconButton(
            onClick = { ChatUiIntent.CopyMessage(message.id).emit() },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Rounded.ContentCopy,
                contentDescription = stringResource(R.string.copy),
                modifier = Modifier.size(17.dp),
                tint = iconColor
            )
        }
        IconButton(
            onClick = { ChatUiIntent.StartEditMessage(message.id).emit() },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.edit),
                modifier = Modifier.size(17.dp),
                tint = iconColor
            )
        }
        if (!isFirstMessage) {
            IconButton(
                onClick = { ChatUiIntent.BranchFromMessage(message.id).emit() },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.branch_from_message),
                    modifier = Modifier.size(17.dp),
                    tint = iconColor
                )
            }
            if (message.role == MessageRole.Assistant) {
                IconButton(
                    onClick = { ChatUiIntent.RegenerateFromMessage(message.id).emit() },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.regenerate),
                        modifier = Modifier.size(17.dp),
                        tint = iconColor
                    )
                }
            }
            IconButton(
                onClick = { ChatUiIntent.DeleteMessageClick(message.id).emit() },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(17.dp),
                    tint = iconColor
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
    emit: ChatUiIntent.() -> Unit
) {
    var quickActionsExpanded by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val sendButtonColor by animateColorAsState(
        targetValue = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "sendButtonColor"
    )

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f))
                    .height(0.5.dp)
            )

            // - 快捷操作胶囊条（随页面整体上移平推，常驻方便快速操作）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.continue_latest_reply),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.ContinueLast.emit() }
                )
                QuickActionPill(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.impersonate_user),
                    enabled = !isGenerating,
                    onClick = { ChatUiIntent.ImpersonateUser.emit() }
                )
                QuickActionPill(
                    icon = Icons.Rounded.Refresh,
                    label = stringResource(R.string.regenerate_latest_reply),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.RegenerateLast.emit() }
                )
                QuickActionPill(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.summarize_now),
                    enabled = hasAssistantMessage && !isGenerating,
                    onClick = { ChatUiIntent.SummarizeNow.emit() }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                RpScrollableOutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = draft,
                    onValueChange = { ChatUiIntent.ChangeInputDraft(it).emit() },
                    enabled = !isGenerating,
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = {
                        Box {
                            IconButton(
                                onClick = { quickActionsExpanded = true },
                                enabled = !isGenerating
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
                                    text = { Text(stringResource(R.string.regenerate_latest_reply)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    },
                                    enabled = hasAssistantMessage,
                                    onClick = {
                                        quickActionsExpanded = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        ChatUiIntent.RegenerateLast.emit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.continue_latest_reply)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                    },
                                    enabled = hasAssistantMessage,
                                    onClick = {
                                        quickActionsExpanded = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        ChatUiIntent.ContinueLast.emit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.impersonate_user)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Edit, contentDescription = null)
                                    },
                                    onClick = {
                                        quickActionsExpanded = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        ChatUiIntent.ImpersonateUser.emit()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.summarize_now)) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                    },
                                    onClick = {
                                        quickActionsExpanded = false
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        ChatUiIntent.SummarizeNow.emit()
                                    }
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.input_next_story),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.5f
                                )
                            )
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.22f
                        )
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
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
                        if (isGenerating) ChatUiIntent.StopGeneration.emit()
                        else ChatUiIntent.SendMessage.emit()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                            contentDescription = if (isGenerating) stringResource(R.string.stop) else stringResource(
                                R.string.send
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

@Composable
private fun ChatSettingsPage(
    session: ChatSessionItem,
    lorebookState: ChatLorebookState,
    loadState: ChatLoadState,
    emit: ChatUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.chat_settings),
            onBack = { ChatUiIntent.CloseChatSettings.emit() }
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingsSection(title = stringResource(R.string.chat_settings_actions)) {
                    MenuAction(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.regenerate_latest_reply),
                        subtitle = stringResource(R.string.regenerate_latest_reply_desc)
                    ) { ChatUiIntent.RegenerateLast.emit() }
                    MenuAction(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.continue_latest_reply),
                        subtitle = stringResource(R.string.continue_latest_reply_desc)
                    ) { ChatUiIntent.ContinueLast.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Edit,
                        title = stringResource(R.string.impersonate_user),
                        subtitle = stringResource(R.string.impersonate_user_desc)
                    ) { ChatUiIntent.ImpersonateUser.emit() }
                    MenuAction(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.summarize_now),
                        subtitle = stringResource(R.string.summarize_now_desc)
                    ) { ChatUiIntent.SummarizeNow.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restore_previous_summary),
                        subtitle = stringResource(R.string.restore_previous_summary_desc)
                    ) { ChatUiIntent.RestorePreviousSummary.emit() }
                    MenuAction(
                        icon = Icons.Rounded.FileUpload,
                        title = stringResource(R.string.export_chat),
                        subtitle = stringResource(R.string.export_chat_desc),
                        enabled = loadState == ChatLoadState.None
                    ) { ChatUiIntent.ExportChatClick.emit() }
                    MenuAction(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.delete_chat_title),
                        subtitle = stringResource(R.string.delete_chat_desc),
                        iconTint = MaterialTheme.colorScheme.error,
                        enabled = loadState == ChatLoadState.None
                    ) { ChatUiIntent.DeleteSessionClick.emit() }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    SummaryPauseRow(
                        paused = session.autoSummaryPaused,
                        onPausedChange = {
                            ChatUiIntent.ToggleAutoSummaryPaused(it).emit()
                        }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.title),
                        value = session.title,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { ChatUiIntent.SaveTitle(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.current_summary),
                        value = session.summarize,
                        placeholder = stringResource(R.string.no_summary_yet),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveSummary(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_note),
                        value = session.userNote,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveUserNote(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_display_name),
                        value = session.userName,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { ChatUiIntent.SaveUserName(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_persona_description),
                        value = session.userDescription,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveUserDescription(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.creator_notes),
                        value = session.creatorNotes,
                        placeholder = stringResource(R.string.using_character_default_or_empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveCreatorNotes(it).emit() }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.world_book)) {
                    MenuAction(
                        icon = Icons.Rounded.Book,
                        title = stringResource(R.string.world_book_manager),
                        subtitle = stringResource(R.string.world_book_subtitle)
                    ) { ChatUiIntent.OpenWorldBookManager.emit() }
                    SessionLoreSettings(
                        groups = lorebookState.groups,
                        visibleGroups = lorebookState.visibleGroups,
                        query = lorebookState.query,
                        emit = emit
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryPauseRow(
    paused: Boolean,
    onPausedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.pause_auto_summary),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(R.string.pause_auto_summary_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
            )
        }
        Switch(checked = paused, onCheckedChange = onPausedChange)
    }
}

@Composable
private fun SessionLoreSettings(
    groups: List<ChatLorebookGroupItem>,
    visibleGroups: List<ChatLorebookGroupItem>,
    query: String,
    emit: ChatUiIntent.() -> Unit
) {
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val isSearching = query.isNotBlank()

    if (groups.isEmpty()) {
        Text(
            text = stringResource(R.string.no_world_book_entries),
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }

    LorebookSearchField(
        query = query,
        onQueryChange = { ChatUiIntent.ChangeLorebookQuery(it).emit() }
    )
    if (visibleGroups.isEmpty()) {
        Text(
            text = stringResource(R.string.no_world_book_search_results),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    visibleGroups.forEach { group ->
        val expanded = isSearching || group.lorebookId in expandedLorebookIds
        SessionLoreGroup(
            group = group,
            expanded = expanded,
            onExpandedChange = {
                expandedLorebookIds = expandedLorebookIds.toggle(group.lorebookId)
            },
            emit = emit
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
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

@Composable
private fun DialogSwitch(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit
) {
    when (val dialogState = state.dialogState) {
        ChatDialogState.None -> Unit

        is ChatDialogState.SessionLorebook -> ChatSessionLorebookDialog(
            groups = state.lorebookState.groups,
            dialogState = dialogState,
            emit = emit
        )

        is ChatDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.OpenProviderSettings.emit() }
        )

        ChatDialogState.Exporting -> LoadingDialog(
            title = stringResource(R.string.exporting_chat),
            description = stringResource(R.string.export_chat_desc)
        )

        ChatDialogState.Summarizing -> LoadingDialog(
            title = stringResource(R.string.updating_summary),
            description = stringResource(R.string.summarize_now_desc),
            onCancel = { ChatUiIntent.CancelSummary.emit() }
        )

        is ChatDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            onCopyRequest = { ChatUiIntent.CopyPromptItem(it).emit() }
        )

        is ChatDialogState.DeleteSessionConfirm -> AppDangerDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_chat_title),
            message = stringResource(R.string.delete_chat_message, dialogState.sessionTitle),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.ConfirmDeleteSession.emit() }
        )

        is ChatDialogState.DeleteMessageConfirm -> AppDangerDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_message_title),
            message = stringResource(R.string.delete_message_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.ConfirmDeleteMessage(dialogState.messageId).emit() }
        )
    }
}

/** 将单聊世界书状态适配到应用级快捷管理对话框。 */
@Composable
private fun ChatSessionLorebookDialog(
    groups: List<ChatLorebookGroupItem>,
    dialogState: ChatDialogState.SessionLorebook,
    emit: ChatUiIntent.() -> Unit
) {
    // 映射只包含通用 Dialog 所需的展示字段，确认后的持久化仍由单聊状态层负责。
    val dialogGroups = remember(groups) {
        groups.map(ChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(ChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    SessionLorebookDialog(
        groups = dialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        enabledEntryIds = dialogState.enabledEntryIds,
        onQueryChange = { ChatUiIntent.ChangeSessionLorebookDialogQuery(it).emit() },
        onToggleGroup = { ChatUiIntent.ToggleSessionLorebookDialogGroup(it).emit() },
        onToggleEntry = { ChatUiIntent.ToggleSessionLorebookDialogEntry(it).emit() },
        onConfirmSelection = { ChatUiIntent.ConfirmSessionLorebookSelection.emit() },
        onManageWorldBooks = { ChatUiIntent.OpenWorldBookManager.emit() },
        onDismissRequest = { ChatUiIntent.DismissDialog.emit() }
    )
}

/** 转换单聊世界书分组为通用对话框展示模型。 */
private fun ChatLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
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
private fun AutoSaveTextField(
    label: String,
    value: String,
    placeholder: String? = null,
    minLines: Int,
    maxLines: Int,
    singleLine: Boolean = false,
    onSave: (String) -> Unit
) {
    var text by remember(label) { mutableStateOf(value) }
    var isFocused by remember(label) { mutableStateOf(false) }

    LaunchedEffect(value, isFocused) {
        if (!isFocused && text != value) {
            text = value
        }
    }

    LaunchedEffect(text, value) {
        if (text != value) {
            delay(450)
            onSave(text)
        }
    }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { placeholderText ->
        { Text(placeholderText) }
    }

    RpScrollableOutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = placeholderContent,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.38f
                    )
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.58f else 0.38f)
                    )
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
            R.string.connected
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
    hasAvailableProvider: Boolean = true
): String {
    return if (loadState == ChatLoadState.Saving) {
        stringResource(R.string.updating_summary)
    } else if (!hasAvailableProvider && generationState == ChatGenerationState.Idle) {
        stringResource(R.string.no_model_configured)
    } else {
        generationState.label(streamEnabled)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ChatLayoutPreview() {
    AppTheme(dynamicColor = false) {
        ChatLayout(
            uiState = ChatUiState.Normal(
                session = ChatSessionItem(
                    id = 1,
                    title = "The Seventh File on a Rainy Night",
                    summarize = "",
                    userNote = "",
                    userName = "You",
                    userDescription = "",
                    creatorNotes = "",
                    autoSummaryPaused = false,
                    messageCount = 1,
                    enabledLorebookEntryIds = setOf(1)
                ),
                character = ChatCharacterItem(
                    id = 1,
                    name = "Lyra",
                    description = "",
                    personality = "",
                    scenario = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    creatorNotes = "",
                    avatarText = "L",
                    accentColor = DefaultCharacterAccentColor
                ),
                conversationState = ChatConversationState(
                    messages = listOf(
                        ChatMessageUiModel(
                            id = "1",
                            role = MessageRole.Assistant,
                            speaker = "Lyra",
                            content = "## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf",
                            parts = listOf(MessageContentPart.Text("## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf")),
                            time = "02:15",
                            tokenCount = 12
                        )
                    )
                ),
                lorebookState = ChatLorebookState(
                    groups = listOf(
                        ChatLorebookGroupItem(
                            lorebookId = 1,
                            lorebookName = "Fog Harbor",
                            enabledCount = 1,
                            totalCount = 1,
                            entries = listOf(
                                ChatLorebookEntryItem(
                                    1,
                                    1,
                                    "Fog Harbor",
                                    "Old District",
                                    listOf("rain"),
                                    emptyList(),
                                    false,
                                    0,
                                    0,
                                    "",
                                    true
                                )
                            )
                        )
                    )
                ),
                streamEnabled = true,
                dialogState = ChatDialogState.SessionLorebook(
                    query = "",
                    visibleGroups = emptyList(),
                    enabledEntryIds = setOf(1)
                )
            ),
            emit = {}
        )
    }
}

@Composable
private fun AvatarPreview(
    avatarText: String,
    avatarColor: Color,
    image: ImageBitmap?,
    size: Int,
    modifier: Modifier = Modifier
) {
    val cornerRadius = remember(size) { (size * 0.28).coerceAtLeast(8.0).dp }
    Surface(
        modifier = modifier.size(size.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = avatarColor.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, avatarColor.copy(alpha = 0.30f))
    ) {
        if (image == null) {
            RpAvatar(
                text = avatarText,
                color = avatarColor,
                modifier = Modifier.size(size.dp),
                shape = RoundedCornerShape(cornerRadius)
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop
            )
        }
    }
}
