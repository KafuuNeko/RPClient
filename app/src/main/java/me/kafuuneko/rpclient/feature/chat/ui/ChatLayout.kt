package me.kafuuneko.rpclient.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageContentPart
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpMetaPill
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.libs.utils.toggle

@Composable
fun ChatLayout(
    uiState: ChatUiState,
    emit: ChatUiIntent.() -> Unit
) {
    BackHandler { ChatUiIntent.Back.emit() }
    when (uiState) {
        ChatUiState.None, ChatUiState.Finished -> Unit
        is ChatUiState.Normal -> {
            when (uiState.page) {
                ChatPage.Conversation -> ChatNormal(uiState, emit)
                ChatPage.Settings -> ChatSettingsPage(uiState, emit)
            }
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun ChatNormal(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit
) {
    val listState = rememberLazyListState()
    var wasAtBottom by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index == layoutInfo.totalItemsCount - 1
            }
        }.collect { atBottom ->
            wasAtBottom = atBottom
        }
    }

    val lastMessageContent = remember(state.messages) {
        state.messages.lastOrNull()?.content ?: ""
    }
    LaunchedEffect(state.messages.size, lastMessageContent) {
        if (wasAtBottom && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        CustomChatTopBar(
            state = state,
            onBack = { ChatUiIntent.Back.emit() },
            emit = emit
        )
        if (state.isSessionLoreExpanded) {
            SessionLorePanel(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                groups = state.lorebookGroups,
                emit = emit
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ConversationStartHeader(state = state)
            }
            items(state.messages) { message ->
                MessageBubble(
                    message = message,
                    character = state.character,
                    expandedThinkBlockIds = state.expandedThinkBlockIds,
                    editing = message.id == state.editingMessageId,
                    editingDraft = state.editingMessageDraft,
                    emit = emit
                )
            }
        }
        ChatInputBar(
            draft = state.inputDraft,
            isGenerating = state.generationState.isGenerating(),
            emit = emit
        )
    }
}

@Composable
private fun CustomChatTopBar(
    state: ChatUiState.Normal,
    onBack: () -> Unit,
    emit: ChatUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            RpAvatar(
                text = state.character.avatarText,
                color = Color(state.character.accentColor),
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.character.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = buildString {
                        append(state.session.title)
                        val status = state.statusText()
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
            
            IconButton(onClick = { ChatUiIntent.OpenSessionLore.emit() }) {
                Icon(
                    Icons.Rounded.Book,
                    contentDescription = stringResource(R.string.session_world_book),
                    tint = if (state.isSessionLoreExpanded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
            IconButton(onClick = { ChatUiIntent.OpenChatSettings.emit() }) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.generation_params),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
private fun ConversationStartHeader(
    state: ChatUiState.Normal,
    modifier: Modifier = Modifier
) {
    val enabledLorebookCount = state.lorebookGroups.sumOf { it.enabledCount }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(state.character.accentColor).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.character.avatarText,
                    color = Color(state.character.accentColor),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black)
                )
            }
            
            Text(
                text = state.character.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            if (state.character.description.isNotBlank()) {
                Text(
                    text = state.character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RpMetaPill(stringResource(R.string.messages_count, state.session.messageCount))
                RpMetaPill(stringResource(R.string.world_books_enabled, enabledLorebookCount))
                RpMetaPill(
                    if (state.streamEnabled) stringResource(R.string.streaming_on)
                    else stringResource(R.string.streaming_off)
                )
            }
        }
    }
}

@Composable
private fun SessionLorePanel(
    modifier: Modifier = Modifier,
    groups: List<ChatLorebookGroupItem>,
    emit: ChatUiIntent.() -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val filteredGroups = groups.filterForQuery(query)
    val isSearching = query.isNotBlank()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RpSectionHeader(title = stringResource(R.string.session_world_books))
            Text(
                text = stringResource(R.string.session_lore_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
            )
            if (groups.isNotEmpty()) {
                LorebookSearchField(
                    query = query,
                    onQueryChange = { query = it }
                )
            }
            if (groups.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_world_book_entries),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (groups.isNotEmpty() && filteredGroups.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_world_book_search_results),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredGroups, key = { it.lorebookId }) { group ->
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
                    stringResource(R.string.enabled_entries_count, group.enabledCount, group.totalCount),
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
        label = { Text(stringResource(R.string.search_world_books)) },
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
        shape = RoundedCornerShape(8.dp)
    )
}

private fun List<ChatLorebookGroupItem>.filterForQuery(query: String): List<ChatLorebookGroupItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    return mapNotNull { group ->
        val groupMatches = group.lorebookName.contains(normalizedQuery, ignoreCase = true)
        val matchingEntries = group.entries.filter { it.matchesQuery(normalizedQuery) }
        when {
            groupMatches -> group
            matchingEntries.isNotEmpty() -> group.copy(entries = matchingEntries)
            else -> null
        }
    }
}

private fun ChatLorebookEntryItem.matchesQuery(query: String): Boolean {
    return lorebookName.contains(query, ignoreCase = true) ||
        name.contains(query, ignoreCase = true) ||
        content.contains(query, ignoreCase = true) ||
        keywords.any { it.contains(query, ignoreCase = true) } ||
        secondaryKeywords.any { it.contains(query, ignoreCase = true) }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUiModel,
    character: ChatCharacterItem,
    expandedThinkBlockIds: Set<String>,
    editing: Boolean,
    editingDraft: String,
    emit: ChatUiIntent.() -> Unit
) {
    val isUser = message.role == MessageRole.User
    var showActions by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            RpAvatar(
                text = if (message.speaker == character.name) character.avatarText else message.speaker.take(1).uppercase(),
                color = if (message.speaker == character.name) Color(character.accentColor) else Color.Gray,
                modifier = Modifier
                    .padding(end = 8.dp, top = 4.dp)
                    .size(32.dp)
            )
        }
        
        Surface(
            modifier = Modifier
                .widthIn(max = if (isUser) 300.dp else 268.dp)
                .clickable {
                    if (!editing) {
                        showActions = !showActions
                    }
                },
            shape = when (message.role) {
                MessageRole.User -> RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 4.dp
                )
                MessageRole.Assistant -> RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 16.dp
                )
                MessageRole.Narrator -> RoundedCornerShape(12.dp)
            },
            color = when (message.role) {
                MessageRole.User -> MaterialTheme.colorScheme.primary
                MessageRole.Assistant -> MaterialTheme.colorScheme.surface
                MessageRole.Narrator -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
            },
            border = if (message.role == MessageRole.Assistant) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            } else null
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message.speaker,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
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
                
                AnimatedVisibility(
                    visible = showActions && !editing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MessageActions(message, emit)
                        }
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
        message.parts.forEach { part ->
            when (part) {
                is ChatMessageContentPart.Text -> {
                    if (part.content.isNotBlank()) {
                        MarkdownMessageText(
                            content = part.content,
                            isUser = isUser
                        )
                    }
                }

                is ChatMessageContentPart.Think -> ThinkBlock(
                    part = part,
                    expanded = part.id in expandedThinkBlockIds,
                    emit = emit
                )
            }
        }
    }
}

@Composable
private fun MessageEditContent(
    draft: String,
    isUser: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = draft,
            onValueChange = { ChatUiIntent.ChangeEditingMessageDraft(it).emit() },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { ChatUiIntent.SaveEditingMessage.emit() }) {
                Text(
                    stringResource(R.string.save),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = { ChatUiIntent.CancelEditingMessage.emit() }) {
                Text(
                    stringResource(R.string.cancel),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ThinkBlock(
    part: ChatMessageContentPart.Think,
    expanded: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { ChatUiIntent.ToggleThinkBlock(part.id).emit() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.thought_process),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (expanded) stringResource(R.string.hide) else stringResource(R.string.show),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Text(
                    part.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun MessageActions(
    message: ChatMessageUiModel,
    emit: ChatUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val isUser = message.role == MessageRole.User
        val iconColor = if (isUser) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        }
        
        val actionModifier = @Composable { onClick: () -> Unit ->
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(6.dp)
        }
        
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = actionModifier { ChatUiIntent.CopyMessage(message.id).emit() },
            tint = iconColor
        )
        Icon(
            Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.edit),
            modifier = actionModifier { ChatUiIntent.StartEditMessage(message.id).emit() },
            tint = iconColor
        )
        Icon(
            Icons.Rounded.Tune,
            contentDescription = stringResource(R.string.branch_from_message),
            modifier = actionModifier { ChatUiIntent.BranchFromMessage(message.id).emit() },
            tint = iconColor
        )
        if (message.role == MessageRole.Assistant) {
            Icon(
                Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.regenerate),
                modifier = actionModifier { ChatUiIntent.RegenerateFromMessage(message.id).emit() },
                tint = iconColor
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    isGenerating: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    .height(0.8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = draft,
                    onValueChange = { ChatUiIntent.ChangeInputDraft(it).emit() },
                    enabled = !isGenerating,
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    placeholder = { 
                        Text(
                            stringResource(R.string.input_next_story),
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        ) 
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    onClick = {
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
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.chat_settings),
            onBack = { ChatUiIntent.CloseChatSettings.emit() }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.delete_chat_title),
                        subtitle = stringResource(R.string.delete_chat_desc),
                        iconTint = MaterialTheme.colorScheme.error,
                        enabled = state.loadState != ChatLoadState.Deleting
                    ) { ChatUiIntent.DeleteSessionClick.emit() }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    AutoSaveTextField(
                        label = stringResource(R.string.title),
                        value = state.session.title,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { ChatUiIntent.SaveTitle(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.current_summary),
                        value = state.session.summarize,
                        placeholder = stringResource(R.string.no_summary_yet),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveSummary(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_note),
                        value = state.session.userNote,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveUserNote(it).emit() }
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.creator_notes),
                        value = state.session.creatorNotes,
                        placeholder = stringResource(R.string.using_character_default_or_empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { ChatUiIntent.SaveCreatorNotes(it).emit() }
                    )
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.world_book)) {
                    SessionLoreSettings(groups = state.lorebookGroups, emit = emit)
                }
            }
        }
    }
}

@Composable
private fun SessionLoreSettings(
    groups: List<ChatLorebookGroupItem>,
    emit: ChatUiIntent.() -> Unit
) {
    var query by remember { mutableStateOf("") }
    var expandedLorebookIds by remember { mutableStateOf(emptySet<Long>()) }
    val filteredGroups = groups.filterForQuery(query)
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
        onQueryChange = { query = it }
    )
    if (filteredGroups.isEmpty()) {
        Text(
            text = stringResource(R.string.no_world_book_search_results),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    filteredGroups.forEach { group ->
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
        shape = RoundedCornerShape(8.dp),
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
    dialogState: ChatDialogState,
    emit: ChatUiIntent.() -> Unit
) {
    when (dialogState) {
        ChatDialogState.None -> Unit
        is ChatDialogState.DeleteSessionConfirm -> AlertDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.delete_chat_title)) },
            text = { Text(stringResource(R.string.delete_chat_message, dialogState.sessionTitle)) },
            confirmButton = {
                TextButton(onClick = { ChatUiIntent.ConfirmDeleteSession.emit() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { ChatUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
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

    OutlinedTextField(
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
        shape = RoundedCornerShape(8.dp)
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
        shape = RoundedCornerShape(8.dp),
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
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
private fun ChatUiState.Normal.statusText(): String {
    return if (loadState == ChatLoadState.Saving) {
        stringResource(R.string.updating_summary)
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
                    creatorNotes = "",
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
                    accentColor = 0xFF315EFD
                ),
                messages = listOf(
                    ChatMessageUiModel(
                        id = "1",
                        role = MessageRole.Assistant,
                        speaker = "Lyra",
                        content = "## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf",
                        parts = listOf(ChatMessageContentPart.Text("## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf")),
                        time = "02:15",
                        tokenCount = 12
                    )
                ),
                lorebookGroups = listOf(
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
                ),
                isSessionLoreExpanded = true,
                streamEnabled = true
            ),
            emit = {}
        )
    }
}
