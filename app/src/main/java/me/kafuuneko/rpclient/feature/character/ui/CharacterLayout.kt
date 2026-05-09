package me.kafuuneko.rpclient.feature.character.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import me.kafuuneko.rpclient.feature.character.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.character.presentation.CharacterDialogState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterEditorState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterLoadState
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiIntent
import me.kafuuneko.rpclient.feature.character.presentation.CharacterUiState
import me.kafuuneko.rpclient.libs.room.entity.Character
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun CharacterLayout(
    uiState: CharacterUiState,
    emit: CharacterUiIntent.() -> Unit
) {
    BackHandler { CharacterUiIntent.Back.emit() }
    when (uiState) {
        CharacterUiState.None, CharacterUiState.Finished -> Unit
        is CharacterUiState.Normal -> {
            CharacterNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun CharacterNormal(
    state: CharacterUiState.Normal,
    emit: CharacterUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = stringResource(R.string.character_manager),
            onBack = { CharacterUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { CharacterUiIntent.CreateCharacter.emit() }) {
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
                CharacterSelector(
                    characters = state.filteredCharacters(),
                    selectedCharacterId = state.selectedCharacterId,
                    avatarFilePaths = state.avatarFilePaths,
                    isLoading = state.loadState == CharacterLoadState.Loading,
                    emit = emit
                )
            }
            item {
                when (val editorState = state.editorState) {
                    CharacterEditorState.None -> EmptyCharacterPanel(emit)
                    is CharacterEditorState.Editing -> CharacterEditor(
                        form = editorState.form,
                        avatarFilePath = state.avatarFilePaths[editorState.form.avatar],
                        loadState = state.loadState,
                        emit = emit
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    emit: CharacterUiIntent.() -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = { CharacterUiIntent.ChangeSearchText(it).emit() },
        label = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun CharacterSelector(
    characters: List<Character>,
    selectedCharacterId: Long?,
    avatarFilePaths: Map<String, String>,
    isLoading: Boolean,
    emit: CharacterUiIntent.() -> Unit
) {
    if (isLoading) {
        Panel {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.loading))
            }
        }
        return
    }
    if (characters.isEmpty()) {
        Panel {
            Text(stringResource(R.string.no_character_cards), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.no_character_cards_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            )
        }
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(characters, key = { it.id }) { character ->
            CharacterTile(
                character = character,
                avatarFilePath = avatarFilePaths[character.avatar],
                selected = character.id == selectedCharacterId,
                onClick = { CharacterUiIntent.SelectCharacter(character.id).emit() }
            )
        }
    }
}

@Composable
private fun CharacterTile(
    character: Character,
    avatarFilePath: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(190.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarPreview(
                    avatarText = character.avatarText(),
                    avatarColor = character.avatarColor(),
                    imagePath = avatarFilePath,
                    size = 46
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        character.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        character.description.ifBlank { stringResource(R.string.no_description) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            RpTagRow(character.getCharacterTagList(), maxCount = 3)
        }
    }
}

@Composable
private fun EmptyCharacterPanel(
    emit: CharacterUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Person)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(stringResource(R.string.character_editor_empty), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.character_editor_empty_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            Button(onClick = { CharacterUiIntent.CreateCharacter.emit() }) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Composable
private fun CharacterEditor(
    form: CharacterEditForm,
    avatarFilePath: String?,
    loadState: CharacterLoadState,
    emit: CharacterUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarPicker(
                    form = form,
                    imagePath = avatarFilePath,
                    emit = emit
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        if (form.isNew) stringResource(R.string.create_character) else form.name.ifBlank { stringResource(R.string.character) },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        stringResource(R.string.character_editor_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                    )
                }
                IconButton(
                    enabled = loadState != CharacterLoadState.Saving && loadState != CharacterLoadState.Deleting,
                    onClick = { CharacterUiIntent.DeleteCharacterClick.emit() }
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        }
        BasicPanel(form, emit)
        DefinitionPanel(form, emit)
        DialoguePanel(form, emit)
        ActionPanel(form, loadState, emit)
    }
}

@Composable
private fun BasicPanel(
    form: CharacterEditForm,
    emit: CharacterUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(stringResource(R.string.name), form.name) {
            CharacterUiIntent.ChangeName(it).emit()
        }
        FormTextField(stringResource(R.string.character_tags), form.tagsText) {
            CharacterUiIntent.ChangeTagsText(it).emit()
        }
        if (form.parsedTags.isNotEmpty()) {
            RpTagRow(form.parsedTags)
        }
        FormTextField(
            label = stringResource(R.string.character_description),
            value = form.description,
            minLines = 3,
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            onChange = { CharacterUiIntent.ChangeDescription(it).emit() }
        )
    }
}

@Composable
private fun DefinitionPanel(
    form: CharacterEditForm,
    emit: CharacterUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.character_definition))
        FormTextField(
            label = stringResource(R.string.character_personality),
            value = form.personality,
            minLines = 4,
            onChange = { CharacterUiIntent.ChangePersonality(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_scenario),
            value = form.scenario,
            minLines = 4,
            onChange = { CharacterUiIntent.ChangeScenario(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_post_history_instructions),
            value = form.postHistoryInstructions,
            minLines = 4,
            onChange = { CharacterUiIntent.ChangePostHistoryInstructions(it).emit() }
        )
    }
}

@Composable
private fun DialoguePanel(
    form: CharacterEditForm,
    emit: CharacterUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.character_dialogue))
        FormTextField(
            label = stringResource(R.string.character_first_messages),
            value = form.firstMessages,
            minLines = 4,
            leadingIcon = { Icon(Icons.Rounded.ChatBubble, contentDescription = null) },
            onChange = { CharacterUiIntent.ChangeFirstMessages(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_examples_of_dialogue),
            value = form.examplesOfDialogue,
            minLines = 5,
            onChange = { CharacterUiIntent.ChangeExamplesOfDialogue(it).emit() }
        )
    }
}

@Composable
private fun ActionPanel(
    form: CharacterEditForm,
    loadState: CharacterLoadState,
    emit: CharacterUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = loadState != CharacterLoadState.Saving && loadState != CharacterLoadState.Deleting,
            onClick = { CharacterUiIntent.CreateCharacter.emit() }
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.create))
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = loadState != CharacterLoadState.Saving && loadState != CharacterLoadState.Deleting,
            onClick = { CharacterUiIntent.SaveCharacter.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    loadState == CharacterLoadState.Saving -> stringResource(R.string.saving)
                    form.isNew -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: CharacterDialogState,
    emit: CharacterUiIntent.() -> Unit
) {
    when (dialogState) {
        CharacterDialogState.None -> Unit
        is CharacterDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = { CharacterUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.delete_character_title)) },
            text = { Text(stringResource(R.string.delete_character_message, dialogState.characterName)) },
            confirmButton = {
                TextButton(onClick = { CharacterUiIntent.ConfirmDeleteCharacter.emit() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { CharacterUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun AvatarPicker(
    form: CharacterEditForm,
    imagePath: String?,
    emit: CharacterUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .size(68.dp)
            .clickable { CharacterUiIntent.PickAvatarClick.emit() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            AvatarPreview(
                avatarText = form.avatarText(),
                avatarColor = form.avatarColor(),
                imagePath = imagePath,
                size = 68
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Rounded.ImageIcon,
                    contentDescription = stringResource(R.string.choose_character_avatar),
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
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

private fun CharacterUiState.Normal.filteredCharacters(): List<Character> {
    val keyword = searchText.trim()
    if (keyword.isEmpty()) return characters
    return characters.filter { character ->
        character.name.contains(keyword, ignoreCase = true) ||
            character.description.contains(keyword, ignoreCase = true) ||
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

private fun CharacterEditForm.avatarText(): String {
    return name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private fun CharacterEditForm.avatarColor(): Color {
    val seed = if (id == 0L) name.hashCode().toLong() else id
    val colors = listOf(0xFF315EFD, 0xFF0F9F8F, 0xFFB55A12, 0xFF8A4FFF, 0xFFB3261E)
    return Color(colors[kotlin.math.abs(seed % colors.size).toInt()])
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterLayout(
            uiState = CharacterUiState.Normal(
                selectedCharacterId = 1L,
                characters = listOf(
                    Character(
                        id = 1L,
                        name = "Character",
                        avatar = "",
                        characterTags = """["Tag"]""",
                        description = "Description",
                        personality = "Personality",
                        scenario = "Scenario",
                        firstMessages = "Hello",
                        examplesOfDialogue = "Example",
                        postHistoryInstructions = "Instructions"
                    )
                ),
                editorState = CharacterEditorState.Editing(
                    CharacterEditForm(
                        id = 1L,
                        name = "Character",
                        avatar = "",
                        tagsText = "Tag",
                        description = "Description"
                    )
                )
            ),
            emit = {}
        )
    }
}
