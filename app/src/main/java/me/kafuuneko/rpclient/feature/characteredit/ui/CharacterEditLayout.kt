package me.kafuuneko.rpclient.feature.characteredit.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characteredit.model.CharacterEditForm
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditDialogState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditLoadState
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditMode
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiIntent
import me.kafuuneko.rpclient.feature.characteredit.presentation.CharacterEditUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

@Composable
fun CharacterEditLayout(
    uiState: CharacterEditUiState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    BackHandler { CharacterEditUiIntent.Back.emit() }
    when (uiState) {
        CharacterEditUiState.None, CharacterEditUiState.Finished -> Unit
        is CharacterEditUiState.Normal -> {
            CharacterEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun CharacterEditNormal(
    state: CharacterEditUiState.Normal,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = if (state.mode == CharacterEditMode.Create) {
                stringResource(R.string.create_character)
            } else {
                stringResource(R.string.edit_character_title)
            },
            onBack = { CharacterEditUiIntent.Back.emit() },
            actions = {
                TopBarSaveButton(state.mode, state.loadState, emit)
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
                    title = state.form.name.ifBlank {
                        if (state.mode == CharacterEditMode.Create) {
                            stringResource(R.string.create_character)
                        } else {
                            stringResource(R.string.character)
                        }
                    },
                    subtitle = stringResource(R.string.character_editor_subtitle)
                )
            }
            if (state.loadState == CharacterEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item { HeaderPanel(state.form, state.avatarFilePath, state.loadState, emit) }
                item { BasicPanel(state.form, emit) }
                item { TagsPanel(state.form.tags, emit) }
                item { DefinitionPanel(state.form, emit) }
                item { FirstMessagesPanel(state.form.firstMessages, emit) }
                item { DialoguePanel(state.form, emit) }
                item { ActionPanel(state.mode, state.loadState, emit) }
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
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
}

@Composable
private fun HeaderPanel(
    form: CharacterEditForm,
    avatarFilePath: String?,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarPicker(form, avatarFilePath, emit)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = form.name.ifBlank { stringResource(R.string.character) },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.tap_avatar_to_choose),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            IconButton(
                enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
                onClick = { CharacterEditUiIntent.DeleteCharacterClick.emit() }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun BasicPanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.basic_info))
        FormTextField(stringResource(R.string.name), form.name) {
            CharacterEditUiIntent.ChangeName(it).emit()
        }
        FormTextField(
            label = stringResource(R.string.character_description),
            value = form.description,
            minLines = 3,
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            onChange = { CharacterEditUiIntent.ChangeDescription(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_creator_notes),
            value = form.creatorNotes,
            minLines = 3,
            onChange = { CharacterEditUiIntent.ChangeCreatorNotes(it).emit() }
        )
    }
}

@Composable
private fun TagsPanel(
    tags: List<String>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(
            title = stringResource(R.string.character_tags),
            action = stringResource(R.string.add),
            onAction = { CharacterEditUiIntent.AddTag.emit() }
        )
        tags.forEachIndexed { index, tag ->
            ListTextField(
                label = stringResource(R.string.character_tag_index, index + 1),
                value = tag,
                onValueChange = { CharacterEditUiIntent.ChangeTag(index, it).emit() },
                onDelete = { CharacterEditUiIntent.DeleteTag(index).emit() }
            )
        }
    }
}

@Composable
private fun DefinitionPanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.character_definition))
        FormTextField(
            label = stringResource(R.string.character_personality),
            value = form.personality,
            minLines = 4,
            onChange = { CharacterEditUiIntent.ChangePersonality(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_scenario),
            value = form.scenario,
            minLines = 4,
            onChange = { CharacterEditUiIntent.ChangeScenario(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_post_history_instructions),
            value = form.postHistoryInstructions,
            minLines = 4,
            onChange = { CharacterEditUiIntent.ChangePostHistoryInstructions(it).emit() }
        )
    }
}

@Composable
private fun FirstMessagesPanel(
    firstMessages: List<String>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(
            title = stringResource(R.string.character_first_messages),
            action = stringResource(R.string.add),
            onAction = { CharacterEditUiIntent.AddFirstMessage.emit() }
        )
        firstMessages.forEachIndexed { index, message ->
            ListTextField(
                label = stringResource(R.string.character_first_message_index, index + 1),
                value = message,
                minLines = 3,
                leadingIcon = { Icon(Icons.Rounded.ChatBubble, contentDescription = null) },
                onValueChange = { CharacterEditUiIntent.ChangeFirstMessage(index, it).emit() },
                onDelete = { CharacterEditUiIntent.DeleteFirstMessage(index).emit() }
            )
        }
    }
}

@Composable
private fun DialoguePanel(
    form: CharacterEditForm,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.character_dialogue))
        FormTextField(
            label = stringResource(R.string.character_examples_of_dialogue),
            value = form.examplesOfDialogue,
            minLines = 5,
            onChange = { CharacterEditUiIntent.ChangeExamplesOfDialogue(it).emit() }
        )
    }
}

@Composable
private fun ActionPanel(
    mode: CharacterEditMode,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
            onClick = { CharacterEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
            onClick = { CharacterEditUiIntent.SaveCharacter.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    loadState == CharacterEditLoadState.Saving -> stringResource(R.string.saving)
                    mode == CharacterEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun TopBarSaveButton(
    mode: CharacterEditMode,
    loadState: CharacterEditLoadState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
        onClick = { CharacterEditUiIntent.SaveCharacter.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                loadState == CharacterEditLoadState.Saving -> stringResource(R.string.saving)
                mode == CharacterEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: CharacterEditDialogState,
    emit: CharacterEditUiIntent.() -> Unit
) {
    when (dialogState) {
        CharacterEditDialogState.None -> Unit
        is CharacterEditDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.delete_character_title)) },
            text = { Text(stringResource(R.string.delete_character_message, dialogState.characterName)) },
            confirmButton = {
                TextButton(onClick = { CharacterEditUiIntent.ConfirmDeleteCharacter.emit() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { CharacterEditUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        CharacterEditDialogState.UnsavedChangesConfirm -> AlertDialog(
            onDismissRequest = { CharacterEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = { CharacterEditUiIntent.ConfirmDiscardChanges.emit() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { CharacterEditUiIntent.DismissDialog.emit() }) {
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
private fun ListTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            minLines = minLines,
            leadingIcon = leadingIcon,
            shape = RoundedCornerShape(8.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delete))
        }
    }
}

@Composable
private fun AvatarPicker(
    form: CharacterEditForm,
    imagePath: String?,
    emit: CharacterEditUiIntent.() -> Unit
) {
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clickable { CharacterEditUiIntent.PickAvatarClick.emit() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            AvatarPreview(
                avatarText = form.avatarText(),
                avatarColor = form.avatarColor(),
                imagePath = imagePath,
                size = 72
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
private fun CharacterEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterEditLayout(
            uiState = CharacterEditUiState.Normal(
                mode = CharacterEditMode.Edit,
                form = CharacterEditForm(
                    id = 1L,
                    name = "Character",
                    tags = listOf("Tag"),
                    description = "Description",
                    creatorNotes = "Notes",
                    firstMessages = listOf("Hello")
                )
            ),
            emit = {}
        )
    }
}
