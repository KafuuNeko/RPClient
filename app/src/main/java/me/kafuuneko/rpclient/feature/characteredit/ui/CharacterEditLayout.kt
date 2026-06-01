package me.kafuuneko.rpclient.feature.characteredit.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import me.kafuuneko.rpclient.libs.room.entity.Lorebook
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
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel
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
                item { AdvancedPanel(state.form, state.availableLorebooks, emit) }
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.character_creator),
                value = form.creator,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeCreator(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.character_version),
                value = form.characterVersion,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeCharacterVersion(it).emit() }
            )
        }
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
private fun AdvancedPanel(
    form: CharacterEditForm,
    availableLorebooks: List<Lorebook>,
    emit: CharacterEditUiIntent.() -> Unit
) {
    var isExtensionsExpanded by rememberSaveable(form.id) { mutableStateOf(false) }
    Panel {
        RpSectionHeader(title = stringResource(R.string.advanced_definition))
        LorebookSelector(
            selectedId = form.characterLorebookId,
            availableLorebooks = availableLorebooks,
            onSelect = { CharacterEditUiIntent.UpdateCharacterLorebook(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_main_prompt_override),
            value = form.systemPrompt,
            minLines = 4,
            maxLines = 8,
            onChange = { CharacterEditUiIntent.ChangeSystemPrompt(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_post_history_instructions),
            value = form.postHistoryInstructions,
            minLines = 4,
            maxLines = 8,
            onChange = { CharacterEditUiIntent.ChangePostHistoryInstructions(it).emit() }
        )
        FormTextField(
            label = stringResource(R.string.character_note),
            value = form.depthPromptPrompt,
            minLines = 4,
            maxLines = 8,
            onChange = { CharacterEditUiIntent.ChangeDepthPromptPrompt(it).emit() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.character_note_depth),
                value = form.depthPromptDepth,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeDepthPromptDepth(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.character_note_role),
                value = form.depthPromptRole,
                modifier = Modifier.weight(1f),
                onChange = { CharacterEditUiIntent.ChangeDepthPromptRole(it).emit() }
            )
        }
        RpSectionHeader(
            title = stringResource(R.string.character_alternate_greetings),
            action = stringResource(R.string.add),
            onAction = { CharacterEditUiIntent.AddAlternateGreeting.emit() }
        )
        form.alternateGreetings.forEachIndexed { index, greeting ->
            ListTextField(
                label = stringResource(R.string.character_alternate_greeting_index, index + 1),
                value = greeting,
                minLines = 3,
                maxLines = 6,
                onValueChange = { CharacterEditUiIntent.ChangeAlternateGreeting(index, it).emit() },
                onDelete = { CharacterEditUiIntent.DeleteAlternateGreeting(index).emit() }
            )
        }
        RawExtensionsPanel(
            value = form.extensionsJson,
            expanded = isExtensionsExpanded,
            onExpandedChange = { isExtensionsExpanded = it },
            onChange = { CharacterEditUiIntent.ChangeExtensionsJson(it).emit() }
        )
    }
}

@Composable
private fun RawExtensionsPanel(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.extensions_json), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.extensions_json_size, value.length),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                    )
                }
                TextButton(onClick = { onExpandedChange(!expanded) }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.Edit,
                        contentDescription = null
                    )
                    Text(if (expanded) stringResource(R.string.hide) else stringResource(R.string.edit))
                }
            }
            if (expanded) {
                FormTextField(
                    label = stringResource(R.string.extensions_json),
                    value = value,
                    minLines = 4,
                    maxLines = 8,
                    onChange = onChange
                )
            } else {
                Text(
                    text = value.ifBlank { "{}" }.compactPreview(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
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
            shape = RoundedCornerShape(12.dp),
            enabled = loadState != CharacterEditLoadState.Saving && loadState != CharacterEditLoadState.Deleting,
            onClick = { CharacterEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
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
private fun FormTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines.coerceAtLeast(6) else 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .then(if (maxLines > 1) Modifier.heightIn(max = 220.dp) else Modifier),
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        maxLines = maxLines.coerceAtLeast(minLines),
        leadingIcon = leadingIcon,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun ListTextField(
    label: String,
    value: String,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) minLines.coerceAtLeast(6) else 1,
    leadingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier
                .weight(1f)
                .then(if (maxLines > 1) Modifier.heightIn(max = 220.dp) else Modifier),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            minLines = minLines,
            maxLines = maxLines.coerceAtLeast(minLines),
            leadingIcon = leadingIcon,
            shape = RoundedCornerShape(12.dp)
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
        shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(12.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LorebookSelector(
    selectedId: Long,
    availableLorebooks: List<Lorebook>,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = if (selectedId == 0L) stringResource(R.string.none)
    else availableLorebooks.find { it.id == selectedId }?.name ?: stringResource(R.string.unknown_world_book)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.associated_world_book)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.none)) },
                onClick = {
                    onSelect(0L)
                    expanded = false
                }
            )
            availableLorebooks.forEach { lorebook ->
                DropdownMenuItem(
                    text = { Text(lorebook.name.ifBlank { "Untitled" }) },
                    onClick = {
                        onSelect(lorebook.id)
                        expanded = false
                    }
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
            modifier = Modifier.size(size.dp),
            shape = RoundedCornerShape(12.dp)
        )
    } else {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(12.dp)),
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

private fun String.compactPreview(limit: Int = 240): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return if (compact.length <= limit) compact else compact.take(limit).trimEnd() + "..."
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
