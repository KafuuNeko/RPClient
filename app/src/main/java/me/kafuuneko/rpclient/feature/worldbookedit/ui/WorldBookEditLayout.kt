package me.kafuuneko.rpclient.feature.worldbookedit.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEntryListItem
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditDialogState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditLoadState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditMode
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiState
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpPanel as Panel
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 世界书元数据与条目列表编辑页 Compose 入口。 */
@Composable
fun WorldBookEditLayout(
    uiState: WorldBookEditUiState,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is WorldBookEditUiState.Normal) { WorldBookEditUiIntent.Back.emit() }
    when (uiState) {
        WorldBookEditUiState.None -> Unit
        is WorldBookEditUiState.Finished -> WorldBookEditLayout(uiState.previous) {}
        is WorldBookEditUiState.Normal -> {
            WorldBookEditNormal(uiState, emit)
            DialogSwitch(uiState.dialogState, emit)
        }
    }
}

@Composable
private fun WorldBookEditNormal(
    state: WorldBookEditUiState.Normal,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = if (state.mode == WorldBookEditMode.Create) stringResource(R.string.create_world_book) else stringResource(R.string.edit_world_book_title),
            onBack = { WorldBookEditUiIntent.Back.emit() },
            actions = {
                TopBarSaveButton(state, emit)
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
                    title = state.form.name.ifBlank { stringResource(R.string.world_book_title) },
                    subtitle = stringResource(R.string.world_book_editor_subtitle)
                )
            }
            if (state.loadState == WorldBookEditLoadState.Loading) {
                item { LoadingPanel() }
            } else {
                item { BasicPanel(state.form, state.loadState, emit) }
                item { EntryHeader(emit) }
                if (state.form.entries.isEmpty()) {
                    item { EmptyEntriesPanel() }
                }
                state.form.entries.forEach { entry ->
                    item {
                        EntryCard(
                            entry = entry,
                            onClick = { WorldBookEditUiIntent.EditEntry(entry.id).emit() }
                        )
                    }
                }
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
    form: WorldBookEditForm,
    loadState: WorldBookEditLoadState,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.AutoMirrored.Rounded.MenuBook)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = form.name.ifBlank { stringResource(R.string.world_book_title) },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.entry_count, form.entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            IconButton(
                enabled = loadState == WorldBookEditLoadState.None,
                onClick = { WorldBookEditUiIntent.DeleteWorldBookClick.emit() }
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.delete))
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.name,
            onValueChange = { WorldBookEditUiIntent.ChangeName(it).emit() },
            label = { Text(stringResource(R.string.name)) },
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EntryHeader(emit: WorldBookEditUiIntent.() -> Unit) {
    RpSectionHeader(
        title = stringResource(R.string.entries),
        action = stringResource(R.string.add),
        onAction = { WorldBookEditUiIntent.AddEntry.emit() }
    )
}

@Composable
private fun EmptyEntriesPanel() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Description)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.no_world_book_entries),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.no_world_book_entries_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: WorldBookEntryListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Description)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(
                    tags = entry.displayTags(stringResource(R.string.entry_constant), stringResource(R.string.no_keywords)),
                    maxCount = 3
                )
                Text(
                    text = stringResource(R.string.entry_order_depth, entry.order, entry.depth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.edit))
        }
    }
}

private fun WorldBookEntryListItem.displayTags(
    constantLabel: String,
    noKeywordsLabel: String
): List<String> {
    return buildList {
        if (constant) add(constantLabel)
        addAll(keywords)
        if (isEmpty()) add(noKeywordsLabel)
    }
}

@Composable
private fun ActionPanel(
    state: WorldBookEditUiState.Normal,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEditLoadState.None,
            onClick = { WorldBookEditUiIntent.Back.emit() }
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            enabled = state.loadState == WorldBookEditLoadState.None,
            onClick = { WorldBookEditUiIntent.SaveWorldBook.emit() }
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Text(
                when {
                    state.loadState == WorldBookEditLoadState.Saving -> stringResource(R.string.saving)
                    state.mode == WorldBookEditMode.Create -> stringResource(R.string.create)
                    else -> stringResource(R.string.save)
                }
            )
        }
    }
}

@Composable
private fun TopBarSaveButton(
    state: WorldBookEditUiState.Normal,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    TextButton(
        enabled = state.loadState == WorldBookEditLoadState.None,
        onClick = { WorldBookEditUiIntent.SaveWorldBook.emit() }
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null)
        Text(
            when {
                state.loadState == WorldBookEditLoadState.Saving -> stringResource(R.string.saving)
                state.mode == WorldBookEditMode.Create -> stringResource(R.string.create)
                else -> stringResource(R.string.save)
            }
        )
    }
}

@Composable
private fun DialogSwitch(
    dialogState: WorldBookEditDialogState,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    when (dialogState) {
        WorldBookEditDialogState.None -> Unit
        is WorldBookEditDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = { WorldBookEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.delete_world_book_title)) },
            text = { Text(stringResource(R.string.delete_world_book_message, dialogState.worldBookName)) },
            confirmButton = {
                TextButton(onClick = { WorldBookEditUiIntent.ConfirmDeleteWorldBook.emit() }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { WorldBookEditUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
        WorldBookEditDialogState.UnsavedChangesConfirm -> AlertDialog(
            onDismissRequest = { WorldBookEditUiIntent.DismissDialog.emit() },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_message)) },
            confirmButton = {
                TextButton(onClick = { WorldBookEditUiIntent.ConfirmDiscardChanges.emit() }) {
                    Text(stringResource(R.string.discard_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { WorldBookEditUiIntent.DismissDialog.emit() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun WorldBookEditLayoutPreview() {
    AppTheme(dynamicColor = false) {
        WorldBookEditLayout(
            uiState = WorldBookEditUiState.Normal(
                mode = WorldBookEditMode.Edit,
                form = WorldBookEditForm(
                    id = 1L,
                    name = "World Setting",
                    entries = listOf(
                        WorldBookEntryListItem(
                            id = 1L,
                            name = "Old District",
                            keywords = listOf("district", "railway"),
                            constant = false,
                            order = 100,
                            depth = 0
                        )
                    )
                )
            ),
            emit = {}
        )
    }
}
