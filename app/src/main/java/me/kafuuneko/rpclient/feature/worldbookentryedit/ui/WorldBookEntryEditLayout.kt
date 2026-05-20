package me.kafuuneko.rpclient.feature.worldbookentryedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Tag
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookentryedit.model.WorldBookEntryEditForm
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditDialogState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditLoadState
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditMode
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookentryedit.presentation.WorldBookEntryEditUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader

@Composable
fun WorldBookEntryEditLayout(
    uiState: WorldBookEntryEditUiState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    BackHandler { WorldBookEntryEditUiIntent.Back.emit() }
    when (uiState) {
        WorldBookEntryEditUiState.None, WorldBookEntryEditUiState.Finished -> Unit
        is WorldBookEntryEditUiState.Normal -> {
            WorldBookEntryEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun WorldBookEntryEditNormal(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AppTopBar(
            title = if (state.mode == WorldBookEntryEditMode.Create) stringResource(R.string.create_world_book_entry) else stringResource(R.string.edit_world_book_entry),
            onBack = { WorldBookEntryEditUiIntent.Back.emit() },
            actions = { TopBarSaveButton(state, emit) }
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
                    title = state.form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    subtitle = stringResource(R.string.world_book_entry_editor_subtitle)
                )
            }
            if (state.loadState == WorldBookEntryEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item { BasicPanel(state.form, state.loadState, emit) }
                item { ContentPanel(state.form, emit) }
                item { KeywordsPanel(state.form, emit) }
                item { AdvancedPanel(state.form, state.loadState, emit) }
                item { ActionPanel(state, emit) }
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
private fun BasicPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Description)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = form.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.entry_order_depth, form.order.toIntOrNull() ?: 0, form.depth.toIntOrNull() ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            IconButton(
                enabled = loadState == WorldBookEntryEditLoadState.None,
                onClick = { WorldBookEntryEditUiIntent.DeleteEntryClick.emit() }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        FormTextField(
            label = stringResource(R.string.entry_name),
            value = form.name,
            onChange = { WorldBookEntryEditUiIntent.ChangeName(it).emit() }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.entry_constant),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.entry_constant_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            Switch(
                checked = form.constant,
                enabled = loadState == WorldBookEntryEditLoadState.None,
                onCheckedChange = { WorldBookEntryEditUiIntent.ChangeConstant(it).emit() }
            )
        }
        ToggleRow(
            title = stringResource(R.string.entry_disabled),
            description = stringResource(R.string.entry_disabled_desc),
            checked = form.disabled,
            enabled = loadState == WorldBookEntryEditLoadState.None,
            onCheckedChange = { WorldBookEntryEditUiIntent.ChangeDisabled(it).emit() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_order),
                value = form.order,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeOrder(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_depth),
                value = form.depth,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeDepth(it).emit() }
            )
        }
    }
}

@Composable
private fun ContentPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.entry_content))
        FormTextField(
            label = stringResource(R.string.entry_content),
            value = form.content,
            minLines = 6,
            onChange = { WorldBookEntryEditUiIntent.ChangeContent(it).emit() }
        )
    }
}

@Composable
private fun KeywordsPanel(
    form: WorldBookEntryEditForm,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        StringListPanel(
            title = stringResource(R.string.primary_keywords),
            values = form.keywords,
            onAdd = { WorldBookEntryEditUiIntent.AddKeyword.emit() },
            onChange = { index, value -> WorldBookEntryEditUiIntent.ChangeKeyword(index, value).emit() },
            onDelete = { WorldBookEntryEditUiIntent.DeleteKeyword(it).emit() }
        )
        StringListPanel(
            title = stringResource(R.string.secondary_keywords),
            values = form.secondaryKeywords,
            onAdd = { WorldBookEntryEditUiIntent.AddSecondaryKeyword.emit() },
            onChange = { index, value -> WorldBookEntryEditUiIntent.ChangeSecondaryKeyword(index, value).emit() },
            onDelete = { WorldBookEntryEditUiIntent.DeleteSecondaryKeyword(it).emit() }
        )
        StringListPanel(
            title = stringResource(R.string.categories),
            values = form.category,
            onAdd = { WorldBookEntryEditUiIntent.AddCategory.emit() },
            onChange = { index, value -> WorldBookEntryEditUiIntent.ChangeCategory(index, value).emit() },
            onDelete = { WorldBookEntryEditUiIntent.DeleteCategory(it).emit() }
        )
    }
}

@Composable
private fun AdvancedPanel(
    form: WorldBookEntryEditForm,
    loadState: WorldBookEntryEditLoadState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Panel {
        RpSectionHeader(title = stringResource(R.string.advanced_definition))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_position),
                value = form.position,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangePosition(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_role),
                value = form.role,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeRole(it).emit() }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_probability),
                value = form.probability,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeProbability(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_logic),
                value = form.selectiveLogic,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeSelectiveLogic(it).emit() }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_scan_depth),
                value = form.scanDepth,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeScanDepth(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_outlet),
                value = form.outletName,
                modifier = Modifier.weight(1f),
                onChange = { WorldBookEntryEditUiIntent.ChangeOutletName(it).emit() }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FormTextField(
                label = stringResource(R.string.entry_sticky),
                value = form.sticky,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeSticky(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_cooldown),
                value = form.cooldown,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeCooldown(it).emit() }
            )
            FormTextField(
                label = stringResource(R.string.entry_delay),
                value = form.delay,
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onChange = { WorldBookEntryEditUiIntent.ChangeDelay(it).emit() }
            )
        }
        ToggleRow(stringResource(R.string.entry_ignore_budget), "", form.ignoreBudget, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeIgnoreBudget(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_whole_words), "", form.matchWholeWords, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeMatchWholeWords(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_case_sensitive), "", form.caseSensitive, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeCaseSensitive(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_prevent_recursion), "", form.preventRecursion, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangePreventRecursion(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_delay_until_recursion), "", form.delayUntilRecursion, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeDelayUntilRecursion(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_match_description), "", form.matchCharacterDescription, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterDescription(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_match_personality), "", form.matchCharacterPersonality, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterPersonality(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_match_character_note), "", form.matchCharacterDepthPrompt, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeMatchCharacterDepthPrompt(it).emit()
        }
        ToggleRow(stringResource(R.string.entry_match_scenario), "", form.matchScenario, loadState == WorldBookEntryEditLoadState.None) {
            WorldBookEntryEditUiIntent.ChangeMatchScenario(it).emit()
        }
        FormTextField(
            label = stringResource(R.string.extensions_json),
            value = form.extensionsJson,
            minLines = 4,
            onChange = { WorldBookEntryEditUiIntent.ChangeExtensionsJson(it).emit() }
        )
    }
}

@Composable
private fun StringListPanel(
    title: String,
    values: List<String>,
    onAdd: () -> Unit,
    onChange: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RpSectionHeader(title = title, action = stringResource(R.string.add), onAction = onAdd)
        values.forEachIndexed { index, value ->
            ListTextField(
                label = stringResource(R.string.indexed_label, title, index + 1),
                value = value,
                onValueChange = { onChange(index, it) },
                onDelete = { onDelete(index) }
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ActionPanel(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = state.loadState == WorldBookEntryEditLoadState.None,
            onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                    state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun TopBarSaveButton(
    state: WorldBookEntryEditUiState.Normal,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState == WorldBookEntryEditLoadState.None,
        onClick = { WorldBookEntryEditUiIntent.SaveEntry.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                state.loadState == WorldBookEntryEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == WorldBookEntryEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: WorldBookEntryEditDialogState,
    emit: WorldBookEntryEditUiIntent.() -> Unit
) {
    when (dialogState) {
        WorldBookEntryEditDialogState.None -> Unit
        is WorldBookEntryEditDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.delete_world_book_entry_title)) },
            text = { Text(stringResource(R.string.delete_world_book_entry_message, dialogState.entryName)) },
            confirmButton = {
                TextButton(onClick = { WorldBookEntryEditUiIntent.ConfirmDeleteEntry.emit() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { WorldBookEntryEditUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        WorldBookEntryEditDialogState.UnsavedChangesConfirm -> AlertDialog(
            onDismissRequest = { WorldBookEntryEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = { WorldBookEntryEditUiIntent.ConfirmDiscardChanges.emit() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { WorldBookEntryEditUiIntent.DismissDialog.emit() }) {
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
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun ListTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null) },
            shape = RoundedCornerShape(8.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.delete))
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookEntryEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookEntryEditLayout(
            uiState = WorldBookEntryEditUiState.Normal(
                mode = WorldBookEntryEditMode.Edit,
                form = WorldBookEntryEditForm(
                    id = 1L,
                    lorebookId = 1L,
                    name = "Old District",
                    keywords = listOf("district", "railway"),
                    secondaryKeywords = listOf("archive"),
                    category = listOf("location"),
                    content = "The old district is divided by three elevated railways."
                )
            ),
            emit = {}
        )
    }
}
