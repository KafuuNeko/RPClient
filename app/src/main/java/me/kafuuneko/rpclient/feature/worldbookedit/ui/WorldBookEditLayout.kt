package me.kafuuneko.rpclient.feature.worldbookedit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookBudgetMode
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEditForm
import me.kafuuneko.rpclient.feature.worldbookedit.model.WorldBookEntryListItem
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditDialogState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEntryFilter
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditLoadState
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditMode
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiIntent
import me.kafuuneko.rpclient.feature.worldbookedit.presentation.WorldBookEditUiState
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
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
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
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
                item { BasicPanel(state.form, state.entryListState.activeCount, state.loadState, emit) }
                item { EntryHeader(state.entryListState.totalCount, emit) }
                if (state.entryListState.totalCount > 0) {
                    item {
                        EntrySearchBar(
                            query = state.entryListState.query,
                            onQueryChange = { WorldBookEditUiIntent.ChangeEntrySearchQuery(it).emit() },
                            filterMode = state.entryListState.filter,
                            onFilterChange = { WorldBookEditUiIntent.SelectEntryFilter(it).emit() }
                        )
                    }
                }
                if (state.entryListState.visibleEntries.isEmpty()) {
                    item { EmptyEntriesPanel(hasEntries = state.entryListState.totalCount > 0) }
                }
                state.entryListState.visibleEntries.forEach { entry ->
                    item(key = entry.id) {
                        EntryCard(
                            entry = entry,
                            onClick = { WorldBookEditUiIntent.EditEntry(entry.id).emit() },
                            onToggleDisabled = { WorldBookEditUiIntent.ToggleEntryDisabled(entry.id, it).emit() }
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
    activeCount: Int,
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
                    text = stringResource(
                        R.string.world_book_stats_format,
                        stringResource(R.string.entry_count, form.entries.size),
                        stringResource(R.string.active_entries_count, activeCount)
                    ),
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
            enabled = loadState == WorldBookEditLoadState.None,
            shape = RoundedCornerShape(12.dp)
        )
        WorldBookBudgetEditor(
            form = form,
            enabled = loadState == WorldBookEditLoadState.None,
            emit = emit
        )
    }
}

@Composable
private fun WorldBookBudgetEditor(
    form: WorldBookEditForm,
    enabled: Boolean,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.world_book_token_budget),
            style = MaterialTheme.typography.titleSmall
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WorldBookBudgetMode.entries.forEach { mode ->
                FilterChip(
                    selected = form.tokenBudgetMode == mode,
                    onClick = { WorldBookEditUiIntent.SelectTokenBudgetMode(mode).emit() },
                    enabled = enabled,
                    label = {
                        Text(stringResource(mode.titleRes()))
                    }
                )
            }
        }
        when (form.tokenBudgetMode) {
            WorldBookBudgetMode.FollowGlobal -> Text(
                text = stringResource(R.string.world_book_budget_follow_global_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WorldBookBudgetMode.FixedTokens -> OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = form.tokenBudgetInput,
                onValueChange = { WorldBookEditUiIntent.ChangeTokenBudgetTokens(it).emit() },
                label = { Text(stringResource(R.string.world_book_budget_fixed_tokens)) },
                supportingText = { Text(stringResource(R.string.world_book_budget_tokens_helper)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = enabled,
                isError = form.resolvedTokenBudget == null,
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

private fun WorldBookBudgetMode.titleRes(): Int {
    return when (this) {
        WorldBookBudgetMode.FollowGlobal -> R.string.world_book_budget_follow_global
        WorldBookBudgetMode.FixedTokens -> R.string.world_book_budget_fixed_tokens
    }
}

@Composable
private fun EntryHeader(
    totalCount: Int,
    emit: WorldBookEditUiIntent.() -> Unit
) {
    RpSectionHeader(
        title = stringResource(R.string.entries_with_count, totalCount),
        action = stringResource(R.string.add),
        onAction = { WorldBookEditUiIntent.AddEntry.emit() }
    )
}

@Composable
private fun EntrySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    filterMode: WorldBookEntryFilter,
    onFilterChange: (WorldBookEntryFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = stringResource(R.string.search_entries_placeholder),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = null)
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = filterMode == WorldBookEntryFilter.All,
                onClick = { onFilterChange(WorldBookEntryFilter.All) },
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = filterMode == WorldBookEntryFilter.Constant,
                onClick = { onFilterChange(WorldBookEntryFilter.Constant) },
                label = { Text(stringResource(R.string.filter_constant)) }
            )
            FilterChip(
                selected = filterMode == WorldBookEntryFilter.Enabled,
                onClick = { onFilterChange(WorldBookEntryFilter.Enabled) },
                label = { Text(stringResource(R.string.filter_enabled)) }
            )
            FilterChip(
                selected = filterMode == WorldBookEntryFilter.Disabled,
                onClick = { onFilterChange(WorldBookEntryFilter.Disabled) },
                label = { Text(stringResource(R.string.filter_disabled)) }
            )
        }
    }
}

@Composable
private fun EmptyEntriesPanel(hasEntries: Boolean = false) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RpIconBubble(Icons.Rounded.Description)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (hasEntries) {
                        stringResource(R.string.no_matching_entries)
                    } else {
                        stringResource(R.string.no_world_book_entries)
                    },
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = if (hasEntries) {
                        stringResource(R.string.no_matching_entries_desc)
                    } else {
                        stringResource(R.string.no_world_book_entries_desc)
                    },
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
    onClick: () -> Unit,
    onToggleDisabled: (Boolean) -> Unit
) {
    val alpha = if (entry.disabled) 0.55f else 1f
    val borderColor = when {
        entry.disabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        entry.constant -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.disabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            entry.disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            entry.constant -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        }
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = entry.name.ifBlank { stringResource(R.string.unnamed_entry) },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.constant) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = stringResource(R.string.entry_constant),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                RpTagRow(
                    tags = entry.displayTags(stringResource(R.string.no_keywords)),
                    maxCount = 3
                )
                Text(
                    text = stringResource(R.string.entry_order_depth, entry.order, entry.depth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = !entry.disabled,
                onCheckedChange = { onToggleDisabled(!it) }
            )
        }
    }
}

private fun WorldBookEntryListItem.displayTags(
    noKeywordsLabel: String
): List<String> {
    return buildList {
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
        is WorldBookEditDialogState.DeleteConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_world_book_title),
            message = stringResource(R.string.delete_world_book_message, dialogState.worldBookName),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEditUiIntent.ConfirmDeleteWorldBook.emit() }
        )
        WorldBookEditDialogState.UnsavedChangesConfirm -> AppDangerDialog(
            onDismissRequest = { WorldBookEditUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.unsaved_changes_title),
            message = stringResource(R.string.unsaved_changes_message),
            confirmText = stringResource(R.string.discard_changes),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { WorldBookEditUiIntent.ConfirmDiscardChanges.emit() }
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
                            disabled = false,
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
