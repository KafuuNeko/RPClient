package me.kafuuneko.rpclient.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.libs.model.ChatSessionUiModel
import me.kafuuneko.rpclient.libs.model.LoreBookUiModel
import me.kafuuneko.rpclient.libs.model.LoreEntryUiModel
import me.kafuuneko.rpclient.libs.model.RpCharacterUiModel
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
        is ChatUiState.Normal -> ChatNormal(uiState, emit)
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
                IconButton(onClick = {}) {
                    Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.generation_params))
                }
            }
        )
        ChatHeader(state)
        if (state.isSessionLoreExpanded) {
            SessionLorePanel(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                state = state,
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
                MessageBubble(message)
            }
        }
        ChatInputBar(state.inputDraft, emit)
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
                    Text(state.generationStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
            RpMetaRow(
                listOf(
                    stringResource(R.string.messages_count, state.session.messageCount),
                    stringResource(R.string.branches_count, state.session.branchCount),
                    stringResource(R.string.world_books_enabled, state.sessionLoreBooks.count { it.enabled })
                )
            )
        }
    }
}

@Composable
private fun SessionLorePanel(
    modifier: Modifier = Modifier,
    state: ChatUiState.Normal,
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
            RpSectionHeader(title = stringResource(R.string.session_world_books), action = stringResource(R.string.manage_world_books)) {
                ChatUiIntent.OpenSessionLore.emit()
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sessionLoreBooks) { loreBook ->
                    SessionLoreBookChip(loreBook, emit)
                }
            }
            Text(
                text = stringResource(R.string.session_lore_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f)
            )
            state.sessionLoreEntries.forEach { entry ->
                SessionLoreEntryRow(entry, emit)
            }
        }
    }
}

@Composable
private fun SessionLoreBookChip(
    loreBook: LoreBookUiModel,
    emit: ChatUiIntent.() -> Unit
) {
    AssistChip(
        onClick = { ChatUiIntent.ToggleSessionLoreBook(loreBook.id).emit() },
        label = { Text(if (loreBook.enabled) loreBook.title else "${loreBook.title} ${stringResource(R.string.disabled)}") },
        leadingIcon = {
            Icon(
                Icons.Rounded.Book,
                contentDescription = null,
                tint = if (loreBook.enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
            )
        }
    )
}

@Composable
private fun SessionLoreEntryRow(
    entry: LoreEntryUiModel,
    emit: ChatUiIntent.() -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RpIconBubble(Icons.Rounded.Book)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall)
            RpTagRow(entry.keywords, maxCount = 2)
        }
        Switch(
            checked = entry.isEnabled,
            onCheckedChange = { ChatUiIntent.ToggleSessionLoreEntry(entry.id).emit() }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessageUiModel) {
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
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
                MessageActions(message.isStreaming)
            }
        }
    }
}

@Composable
private fun MessageActions(isStreaming: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val iconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
        Icon(
            Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.edit),
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = stringResource(R.string.regenerate),
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
        Icon(
            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.Favorite,
            contentDescription = if (isStreaming) stringResource(R.string.stop) else stringResource(R.string.favorite),
            modifier = Modifier.size(16.dp),
            tint = iconColor
        )
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
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
                onValueChange = {},
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                placeholder = { Text(stringResource(R.string.input_next_story)) }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                onClick = { ChatUiIntent.SendMessage.emit() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ChatLayoutPreview() {
    AppTheme(dynamicColor = false) {
        ChatLayout(
            uiState = ChatUiState.Normal(
                session = ChatSessionUiModel("s", "Lyra", "The Seventh File on a Rainy Night", "", 12, 2, "Just now"),
                character = RpCharacterUiModel(
                    "lyra",
                    "Lyra",
                    "Fog Harbor Archivist",
                    "",
                    "L",
                    emptyList(),
                    12,
                    "Just now",
                    0xFF315EFD
                ),
                messages = emptyList(),
                sessionLoreBooks = listOf(
                    LoreBookUiModel(
                        "l",
                        "Fog Harbor Old District",
                        "Chat Lore",
                        18,
                        true,
                        "Today"
                    )
                ),
                sessionLoreEntries = emptyList(),
                isSessionLoreExpanded = true,
                inputDraft = "",
                generationStatus = stringResource(R.string.connected)
            ),
            emit = {}
        )
    }
}