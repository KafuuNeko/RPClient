package me.kafuuneko.rpclient.feature.characterlist.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListLoadState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiIntent
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiState
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun CharacterListLayout(
    uiState: CharacterListUiState,
    emit: CharacterListUiIntent.() -> Unit
) {
    BackHandler { CharacterListUiIntent.Back.emit() }
    when (uiState) {
        CharacterListUiState.None, CharacterListUiState.Finished -> Unit
        is CharacterListUiState.Normal -> CharacterListNormal(uiState, emit)
    }
}

@Composable
private fun CharacterListNormal(
    state: CharacterListUiState.Normal,
    emit: CharacterListUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.character_manager),
            onBack = { CharacterListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { CharacterListUiIntent.ImportCharacterClick.emit() }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = stringResource(R.string.import_character))
                }
                IconButton(onClick = { CharacterListUiIntent.CreateCharacter.emit() }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_character))
                }
            }
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
                    title = stringResource(R.string.character_cards_title),
                    subtitle = stringResource(R.string.character_cards_subtitle)
                )
            }
            item { SearchField(state.searchText, emit) }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.all_characters),
                    action = stringResource(R.string.create),
                    onAction = { CharacterListUiIntent.CreateCharacter.emit() }
                )
            }
            if (state.loadState == CharacterListLoadState.Loading) {
                item { LoadingRow() }
            }
            val characters = state.filteredCharacters()
            if (state.loadState != CharacterListLoadState.Loading && characters.isEmpty()) {
                item { EmptyCharacterCard(emit) }
            }
            items(characters, key = { it.id }) { character ->
                CharacterListCard(
                    character = character,
                    avatarFilePath = state.avatarFilePaths[character.avatar],
                    selected = character.id == state.selectedCharacterId,
                    onClick = { CharacterListUiIntent.SelectCharacter(character.id).emit() },
                    onExport = { CharacterListUiIntent.ExportCharacterJsonClick(character.id).emit() }
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    emit: CharacterListUiIntent.() -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = { CharacterListUiIntent.ChangeSearchText(it).emit() },
        label = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
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
private fun EmptyCharacterCard(
    emit: CharacterListUiIntent.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Person)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(stringResource(R.string.no_character_cards), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.no_character_cards_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            Button(onClick = { CharacterListUiIntent.CreateCharacter.emit() }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Composable
private fun CharacterListCard(
    character: Character,
    avatarFilePath: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        ) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarPreview(
                avatarText = character.avatarText(),
                avatarColor = character.avatarColor(),
                imagePath = avatarFilePath,
                size = 54
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = character.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onExport) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.export_character)
                        )
                    }
                }
                Text(
                    character.description.ifBlank { stringResource(R.string.no_description) },
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
private fun AvatarPreview(
    avatarText: String,
    avatarColor: Color,
    imagePath: String?,
    size: Int
) {
    val bitmap = remember(imagePath) {
        imagePath?.let { BitmapFactory.decodeFile(it) }
    }
    if (bitmap == null) {
        RpAvatar(
            text = avatarText,
            color = avatarColor,
            modifier = Modifier.size(size.dp)
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun CharacterListUiState.Normal.filteredCharacters(): List<Character> {
    val keyword = searchText.trim()
    if (keyword.isEmpty()) return characters
    return characters.filter { character ->
        character.name.contains(keyword, ignoreCase = true) ||
            character.description.contains(keyword, ignoreCase = true) ||
            character.creatorNotes.contains(keyword, ignoreCase = true) ||
            character.personality.contains(keyword, ignoreCase = true) ||
            character.scenario.contains(keyword, ignoreCase = true) ||
            character.postHistoryInstructions.contains(keyword, ignoreCase = true) ||
            character.getCharacterTagList().any { it.contains(keyword, ignoreCase = true) }
    }
}

private fun Character.avatarText(): String {
    return name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun Character.avatarColor(): Color {
    val colors = listOf(0xFF315EFD, 0xFF0F9F8F, 0xFFB55A12, 0xFF8A4FFF, 0xFFB3261E)
    return Color(colors[(id % colors.size).toInt()])
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterListLayout(
            uiState = CharacterListUiState.Normal(
                selectedCharacterId = 1L,
                characters = listOf(
                    Character(
                        id = 1L,
                        name = "Character",
                        avatar = "",
                        characterTags = """["Tag"]""",
                        description = "Description",
                        creatorNotes = "Notes",
                        personality = "Personality",
                        scenario = "Scenario",
                        firstMessages = "Hello",
                        examplesOfDialogue = "Example",
                        postHistoryInstructions = "Instructions"
                    )
                )
            ),
            emit = {}
        )
    }
}
