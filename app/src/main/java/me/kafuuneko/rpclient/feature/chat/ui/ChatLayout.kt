package me.kafuuneko.rpclient.feature.chat.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatGenerationState
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
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
import me.kafuuneko.rpclient.ui.widgets.RpMetaRow
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = state.session.title,
            onBack = { ChatUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { ChatUiIntent.OpenSessionLore.emit() }) {
                    Icon(
                        Icons.Rounded.Book,
                        contentDescription = stringResource(R.string.session_world_book),
                        tint = if (state.isSessionLoreExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { ChatUiIntent.OpenChatSettings.emit() }) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.generation_params))
                }
            }
        )
        ChatHeader(state)
        if (state.isSessionLoreExpanded) {
            SessionLorePanel(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                entries = state.lorebookEntries,
                emit = emit
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages) { message ->
                MessageBubble(
                    message = message,
                    expandedThinkBlockIds = state.expandedThinkBlockIds,
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
private fun ChatHeader(state: ChatUiState.Normal) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RpAvatar(state.character.avatarText, Color(state.character.accentColor))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.character.name, style = MaterialTheme.typography.titleMedium)
                    Text(state.statusText(), style = MaterialTheme.typography.bodySmall)
                }
            }
            RpMetaRow(
                listOf(
                    stringResource(R.string.messages_count, state.session.messageCount),
                    stringResource(R.string.world_books_enabled, state.lorebookEntries.count { it.enabled }),
                    if (state.streamEnabled) stringResource(R.string.streaming_on) else stringResource(R.string.streaming_off)
                )
            )
        }
    }
}

@Composable
private fun SessionLorePanel(
    modifier: Modifier = Modifier,
    entries: List<ChatLorebookEntryItem>,
    emit: ChatUiIntent.() -> Unit
) {
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
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_world_book_entries),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            entries.forEach { entry ->
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
            Text(entry.name.ifBlank { stringResource(R.string.unnamed_entry) }, style = MaterialTheme.typography.titleSmall)
            Text(
                entry.lorebookName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.54f)
            )
            RpTagRow(entry.keywords, maxCount = 2)
        }
        Switch(
            checked = entry.enabled,
            onCheckedChange = { ChatUiIntent.ToggleSessionLoreEntry(entry.id).emit() }
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessageUiModel,
    expandedThinkBlockIds: Set<String>,
    emit: ChatUiIntent.() -> Unit
) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(8.dp),
            color = when (message.role) {
                MessageRole.User -> MaterialTheme.colorScheme.primary
                MessageRole.Assistant -> MaterialTheme.colorScheme.surface
                MessageRole.Narrator -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
            }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.speaker,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                    )
                }
                MessageContent(
                    message = message,
                    expandedThinkBlockIds = expandedThinkBlockIds,
                    isUser = isUser,
                    emit = emit
                )
                MessageActions(message, emit)
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
                        Text(
                            text = part.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
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
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy), modifier = Modifier.size(16.dp), tint = iconColor)
        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp), tint = iconColor)
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.regenerate),
            modifier = Modifier
                .size(16.dp)
                .clickable { ChatUiIntent.RegenerateFromMessage(message.id).emit() },
            tint = iconColor
        )
        Icon(
            imageVector = if (message.isStreaming) Icons.Rounded.Stop else Icons.Rounded.Favorite,
            contentDescription = if (message.isStreaming) stringResource(R.string.stop) else stringResource(R.string.favorite),
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    isGenerating: Boolean,
    emit: ChatUiIntent.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draft,
                onValueChange = { ChatUiIntent.ChangeInputDraft(it).emit() },
                enabled = !isGenerating,
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                placeholder = { Text(stringResource(R.string.input_next_story)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isGenerating) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                onClick = {
                    if (isGenerating) ChatUiIntent.StopGeneration.emit()
                    else ChatUiIntent.SendMessage.emit()
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isGenerating) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,
                        contentDescription = if (isGenerating) stringResource(R.string.stop) else stringResource(R.string.send),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
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
                        title = stringResource(R.string.summarize_now),
                        subtitle = stringResource(R.string.summarize_now_desc)
                    ) { ChatUiIntent.SummarizeNow.emit() }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    MenuAction(Icons.Rounded.Edit, stringResource(R.string.title), state.session.title) { ChatUiIntent.EditTitleClick.emit() }
                    MenuAction(Icons.Rounded.Edit, stringResource(R.string.current_summary), state.session.summarize.ifBlank { stringResource(R.string.no_summary_yet) }) {
                        ChatUiIntent.EditSummaryClick.emit()
                    }
                    MenuAction(Icons.Rounded.Edit, stringResource(R.string.user_note), state.session.userNote.ifBlank { stringResource(R.string.empty) }) {
                        ChatUiIntent.EditUserNoteClick.emit()
                    }
                    MenuAction(Icons.Rounded.Edit, stringResource(R.string.creator_notes), state.session.creatorNotes.ifBlank { stringResource(R.string.using_character_default_or_empty) }) {
                        ChatUiIntent.EditCreatorNotesClick.emit()
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.world_book)) {
                    if (state.lorebookEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_world_book_entries),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    state.lorebookEntries.forEach { entry ->
                        SessionLoreEntryRow(entry, emit)
                    }
                }
            }
        }
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
        is ChatDialogState.EditTitle -> TextEditDialog(stringResource(R.string.title), dialogState.text, { ChatUiIntent.SaveTitle(it).emit() }, emit)
        is ChatDialogState.EditSummary -> TextEditDialog(stringResource(R.string.current_summary), dialogState.text, { ChatUiIntent.SaveSummary(it).emit() }, emit)
        is ChatDialogState.EditUserNote -> TextEditDialog(stringResource(R.string.user_note), dialogState.text, { ChatUiIntent.SaveUserNote(it).emit() }, emit)
        is ChatDialogState.EditCreatorNotes -> TextEditDialog(stringResource(R.string.creator_notes), dialogState.text, { ChatUiIntent.SaveCreatorNotes(it).emit() }, emit)
    }
}

@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initialText: String,
    onSave: (String) -> Unit,
    emit: ChatUiIntent.() -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { ChatUiIntent.DismissDialog.emit() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun ChatGenerationState.isGenerating(): Boolean {
    return this is ChatGenerationState.Requesting || this is ChatGenerationState.Streaming
}

@Composable
private fun ChatGenerationState.label(streamEnabled: Boolean): String {
    return when (this) {
        ChatGenerationState.Idle -> if (streamEnabled) stringResource(R.string.connected_streaming_enabled) else stringResource(R.string.connected)
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
                        content = "The rain kept tapping on the archive windows.",
                        parts = listOf(ChatMessageContentPart.Text("The rain kept tapping on the archive windows.")),
                        time = "02:15",
                        tokenCount = 12
                    )
                ),
                lorebookEntries = listOf(
                    ChatLorebookEntryItem(1, 1, "Fog Harbor", "Old District", listOf("rain"), emptyList(), 0, 0, "", true)
                ),
                isSessionLoreExpanded = true,
                streamEnabled = true
            ),
            emit = {}
        )
    }
}
