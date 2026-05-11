package me.kafuuneko.rpclient.feature.chatcreate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun ChatCreateLayout(
    uiState: ChatCreateUiState,
    emit: ChatCreateUiIntent.() -> Unit
) {
    BackHandler { ChatCreateUiIntent.Back.emit() }
    when (uiState) {
        ChatCreateUiState.None, ChatCreateUiState.Finished -> Unit
        is ChatCreateUiState.Normal -> ChatCreateNormal(uiState, emit)
    }
}

@Composable
private fun ChatCreateNormal(
    state: ChatCreateUiState.Normal,
    emit: ChatCreateUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.create_chat_title),
            onBack = { ChatCreateUiIntent.Back.emit() }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                RpPageTitle(
                    title = stringResource(R.string.create_chat_title),
                    subtitle = stringResource(R.string.create_chat_subtitle)
                )
            }
            if (state.loadState == ChatCreateLoadState.Loading) {
                item { LoadingRow() }
            } else {
                item { BasicForm(state.form, emit) }
                item {
                    RpSectionHeader(title = stringResource(R.string.bind_character))
                }
                if (state.characters.isEmpty()) {
                    item { EmptyCard(Icons.Rounded.Person, stringResource(R.string.no_characters_for_chat)) }
                }
                items(state.characters) { character ->
                    CharacterOption(
                        character = character,
                        selected = character.id == state.form.selectedCharacterId,
                        onClick = { ChatCreateUiIntent.SelectCharacter(character.id).emit() }
                    )
                }
                if (state.selectedCharacterFirstMessages.isNotEmpty()) {
                    item {
                        RpSectionHeader(title = stringResource(R.string.first_messages))
                    }
                    itemsIndexed(state.selectedCharacterFirstMessages) { index, message ->
                        FirstMessageOption(
                            message = message,
                            selected = state.form.selectedFirstMessageIndex == index,
                            onClick = { ChatCreateUiIntent.SelectFirstMessage(index).emit() }
                        )
                    }
                }
                item {
                    RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                }
                if (state.lorebookEntries.isEmpty()) {
                    item {
                        EmptyCard(
                            icon = Icons.Rounded.Book,
                            text = stringResource(R.string.no_world_book_entries_selectable)
                        )
                    }
                }
                items(state.lorebookEntries) { item ->
                    LorebookEntryOption(
                        item = item,
                        selected = item.entry.id in state.form.selectedLorebookEntryIds,
                        onClick = { ChatCreateUiIntent.ToggleLorebookEntry(item.entry.id).emit() }
                    )
                }
                item {
                    CreateButton(
                        loadState = state.loadState,
                        hasCharacter = state.characters.isNotEmpty(),
                        emit = emit
                    )
                }
            }
        }
    }
}

@Composable
private fun BasicForm(
    form: ChatCreateForm,
    emit: ChatCreateUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.title,
            onValueChange = { ChatCreateUiIntent.ChangeTitle(it).emit() },
            label = { Text(stringResource(R.string.chat_title)) },
            leadingIcon = { Icon(Icons.Rounded.AddComment, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.userNote,
            onValueChange = { ChatCreateUiIntent.ChangeUserNote(it).emit() },
            label = { Text(stringResource(R.string.default_user_note)) },
            minLines = 3,
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun CharacterOption(
    character: Character,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.Person)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = character.description.ifBlank { stringResource(R.string.no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(character.getCharacterTagList(), maxCount = 4)
            }
        }
    }
}

@Composable
private fun FirstMessageOption(
    message: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.FormatQuote)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LorebookEntryOption(
    item: ChatCreateLorebookEntryItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(modifier = Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.Book)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = item.entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.entry.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(
                    tags = listOf(
                        item.lorebookName,
                        stringResource(R.string.entry_order_depth, item.entry.order, item.entry.depth)
                    )
                )
            }
        }
    }
}

@Composable
private fun CreateButton(
    loadState: ChatCreateLoadState,
    hasCharacter: Boolean,
    emit: ChatCreateUiIntent.() -> Unit
) {
    val creating = loadState == ChatCreateLoadState.Creating
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = hasCharacter && !creating,
        onClick = { ChatCreateUiIntent.CreateChat.emit() }
    ) {
        if (creating) {
            CircularProgressIndicator()
        } else {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.create_chat))
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ChatCreateLayoutPreview() {
    AppTheme(dynamicColor = false) {
        ChatCreateLayout(
            uiState = ChatCreateUiState.Normal(
                selectedCharacterFirstMessages = listOf(
                    "The rain taps gently against the window as Lyra opens the case file.",
                    "You arrive at the archive just before midnight."
                ),
                characters = listOf(
                    Character(
                        id = 1L,
                        name = "Lyra",
                        avatar = "",
                        characterTags = """["Mystery"]""",
                        description = "Archivist",
                        creatorNotes = "",
                        personality = "",
                        scenario = "",
                        firstMessages = "The rain taps gently against the window as Lyra opens the case file.",
                        examplesOfDialogue = "",
                        postHistoryInstructions = ""
                    )
                ),
                lorebookEntries = listOf(
                    ChatCreateLorebookEntryItem(
                        entry = LorebookEntry(
                            id = 1L,
                            lorebookId = 1L,
                            name = "Old Town",
                            keywords = "[]",
                            secondaryKeywords = "[]",
                            order = 100,
                            depth = 0,
                            category = "[]",
                            content = "Persistent lore content."
                        ),
                        lorebookName = "Fog Harbor"
                    )
                )
            ),
            emit = {}
        )
    }
}
