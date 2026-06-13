package me.kafuuneko.rpclient.feature.regexscript.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.regexscript.model.RegexScriptDraft
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptDialogState
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiIntent
import me.kafuuneko.rpclient.feature.regexscript.presentation.RegexScriptUiState
import me.kafuuneko.rpclient.libs.regex.RegexExecutionMode
import me.kafuuneko.rpclient.libs.regex.RegexFindMacroMode
import me.kafuuneko.rpclient.libs.regex.RegexPlacement
import me.kafuuneko.rpclient.libs.regex.RegexScript
import me.kafuuneko.rpclient.libs.regex.RegexScriptScope
import me.kafuuneko.rpclient.ui.theme.AppTheme

@Composable
fun RegexScriptLayout(
    uiState: RegexScriptUiState,
    emitIntent: (RegexScriptUiIntent) -> Unit = {}
) {
    BackHandler { emitIntent(RegexScriptUiIntent.Back) }
    when (uiState) {
        RegexScriptUiState.None, RegexScriptUiState.Finished -> Unit
        is RegexScriptUiState.Normal -> {
            RegexScriptNormal(uiState, emitIntent)
            DialogSwitch(uiState.dialogState, emitIntent)
        }
    }
}

@Composable
private fun RegexScriptNormal(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    val canManageScripts =
        state.scope != RegexScriptScope.Character || state.selectedCharacterId != null
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { emitIntent(RegexScriptUiIntent.Back) }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Text(
                    text = stringResource(R.string.regex_script_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { emitIntent(RegexScriptUiIntent.ImportClick) },
                    enabled = canManageScripts
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null)
                }
                IconButton(onClick = { emitIntent(RegexScriptUiIntent.ExportClick) }) {
                    Icon(Icons.Rounded.FileUpload, contentDescription = null)
                }
                IconButton(
                    onClick = { emitIntent(RegexScriptUiIntent.CreateScript) },
                    enabled = canManageScripts
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ScopeSelector(state, emitIntent) }
            if (state.scope == RegexScriptScope.Character) {
                item { CharacterSelector(state, emitIntent) }
            }
            if (state.scope != RegexScriptScope.Global) {
                item { AuthorizationCard(state, emitIntent) }
            }
            if (state.scripts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.regex_no_scripts),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.scripts, key = { it.id }) { script ->
                    ScriptCard(script, emitIntent)
                }
            }
            item { TestCard(state, emitIntent) }
        }
    }
}

@Composable
private fun ScopeSelector(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RegexScriptScope.entries.forEach { scope ->
            FilterChip(
                selected = state.scope == scope,
                onClick = { emitIntent(RegexScriptUiIntent.SelectScope(scope)) },
                label = { Text(scope.label()) }
            )
        }
    }
}

@Composable
private fun CharacterSelector(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.characters.forEach { character ->
            FilterChip(
                selected = state.selectedCharacterId == character.id,
                onClick = { emitIntent(RegexScriptUiIntent.SelectCharacter(character.id)) },
                label = { Text(character.name, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun AuthorizationCard(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.regex_authorization), fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.regex_authorization_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = state.authorized,
                enabled = state.scope != RegexScriptScope.Character ||
                    state.selectedCharacterId != null,
                onCheckedChange = {
                    emitIntent(RegexScriptUiIntent.ToggleAuthorization(it))
                }
            )
        }
    }
}

@Composable
private fun ScriptCard(
    script: RegexScript,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    var dragDistance by remember(script.id) { mutableFloatStateOf(0f) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { dragDistance += it },
                onDragStopped = {
                    when {
                        dragDistance > 24f -> emitIntent(
                            RegexScriptUiIntent.MoveScript(script.id, 1)
                        )
                        dragDistance < -24f -> emitIntent(
                            RegexScriptUiIntent.MoveScript(script.id, -1)
                        )
                    }
                    dragDistance = 0f
                }
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.DragHandle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = script.scriptName.ifBlank { stringResource(R.string.regex_unnamed) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = script.findRegex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = !script.disabled,
                onCheckedChange = {
                    emitIntent(RegexScriptUiIntent.ToggleScriptEnabled(script.id))
                }
            )
            IconButton(onClick = { emitIntent(RegexScriptUiIntent.EditScript(script.id)) }) {
                Icon(Icons.Rounded.Edit, contentDescription = null)
            }
            IconButton(onClick = { emitIntent(RegexScriptUiIntent.CopyScript(script.id)) }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null)
            }
            IconButton(onClick = { emitIntent(RegexScriptUiIntent.DeleteScriptClick(script.id)) }) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun TestCard(
    state: RegexScriptUiState.Normal,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.regex_test_title), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RegexPlacement.entries.forEach { placement ->
                    FilterChip(
                        selected = state.testPlacement == placement,
                        onClick = {
                            emitIntent(RegexScriptUiIntent.SelectTestPlacement(placement))
                        },
                        label = { Text(placement.label()) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RegexExecutionMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.testMode == mode,
                        onClick = { emitIntent(RegexScriptUiIntent.SelectTestMode(mode)) },
                        label = { Text(mode.name) }
                    )
                }
            }
            OutlinedTextField(
                value = state.testInput,
                onValueChange = { emitIntent(RegexScriptUiIntent.ChangeTestInput(it)) },
                label = { Text(stringResource(R.string.regex_test_input)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Button(onClick = { emitIntent(RegexScriptUiIntent.RunTest) }) {
                Text(stringResource(R.string.regex_run_test))
            }
            if (state.testOutput.isNotBlank()) {
                OutlinedTextField(
                    value = state.testOutput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.regex_test_output)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        }
    }
}

@Composable
private fun DialogSwitch(
    dialogState: RegexScriptDialogState,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    when (dialogState) {
        RegexScriptDialogState.None -> Unit
        is RegexScriptDialogState.Editor -> EditorDialog(dialogState, emitIntent)
        is RegexScriptDialogState.DeleteConfirm -> AlertDialog(
            onDismissRequest = { emitIntent(RegexScriptUiIntent.DismissDialog) },
            title = { Text(stringResource(R.string.regex_delete_title)) },
            text = { Text(dialogState.scriptName) },
            confirmButton = {
                TextButton(onClick = { emitIntent(RegexScriptUiIntent.ConfirmDeleteScript) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { emitIntent(RegexScriptUiIntent.DismissDialog) }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EditorDialog(
    state: RegexScriptDialogState.Editor,
    emitIntent: (RegexScriptUiIntent) -> Unit
) {
    val draft = state.draft
    AlertDialog(
        onDismissRequest = { emitIntent(RegexScriptUiIntent.DismissDialog) },
        title = { Text(stringResource(R.string.regex_editor_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DraftField(draft.scriptName, R.string.regex_script_name) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(scriptName = it)))
                }
                DraftField(draft.findRegex, R.string.regex_find_regex, minLines = 2) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(findRegex = it)))
                }
                DraftField(draft.replaceString, R.string.regex_replace_string, minLines = 2) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(replaceString = it)))
                }
                DraftField(draft.trimStrings, R.string.regex_trim_strings, minLines = 2) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(trimStrings = it)))
                }
                Text(stringResource(R.string.regex_placements), fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegexPlacement.entries.forEach { placement ->
                        FilterChip(
                            selected = placement.value in draft.placements,
                            onClick = {
                                val placements = draft.placements.toMutableSet()
                                if (!placements.add(placement.value)) placements.remove(placement.value)
                                emitIntent(
                                    RegexScriptUiIntent.UpdateDraft(
                                        draft.copy(placements = placements)
                                    )
                                )
                            },
                            label = { Text(placement.label()) }
                        )
                    }
                }
                BooleanRow(stringResource(R.string.regex_enabled), !draft.disabled) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(disabled = !it)))
                }
                BooleanRow(stringResource(R.string.regex_markdown_only), draft.markdownOnly) {
                    emitIntent(
                        RegexScriptUiIntent.UpdateDraft(
                            draft.copy(
                                markdownOnly = it,
                                promptOnly = if (it) false else draft.promptOnly
                            )
                        )
                    )
                }
                BooleanRow(stringResource(R.string.regex_prompt_only), draft.promptOnly) {
                    emitIntent(
                        RegexScriptUiIntent.UpdateDraft(
                            draft.copy(
                                promptOnly = it,
                                markdownOnly = if (it) false else draft.markdownOnly
                            )
                        )
                    )
                }
                BooleanRow(stringResource(R.string.regex_run_on_edit), draft.runOnEdit) {
                    emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(runOnEdit = it)))
                }
                Text(stringResource(R.string.regex_find_macro_mode), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RegexFindMacroMode.entries.forEach { mode ->
                        FilterChip(
                            selected = draft.substituteRegex == mode.value,
                            onClick = {
                                emitIntent(
                                    RegexScriptUiIntent.UpdateDraft(
                                        draft.copy(substituteRegex = mode.value)
                                    )
                                )
                            },
                            label = { Text(mode.name) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.minDepth,
                        onValueChange = {
                            emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(minDepth = it)))
                        },
                        label = { Text(stringResource(R.string.regex_min_depth)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = draft.maxDepth,
                        onValueChange = {
                            emitIntent(RegexScriptUiIntent.UpdateDraft(draft.copy(maxDepth = it)))
                        },
                        label = { Text(stringResource(R.string.regex_max_depth)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                state.validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { emitIntent(RegexScriptUiIntent.SaveDraft) },
                enabled = state.validationError == null
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = { emitIntent(RegexScriptUiIntent.DismissDialog) }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DraftField(
    value: String,
    labelRes: Int,
    minLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines
    )
}

@Composable
private fun BooleanRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RegexScriptScope.label(): String = when (this) {
    RegexScriptScope.Global -> stringResource(R.string.regex_scope_global)
    RegexScriptScope.Preset -> stringResource(R.string.regex_scope_preset)
    RegexScriptScope.Character -> stringResource(R.string.regex_scope_character)
}

@Composable
private fun RegexPlacement.label(): String = when (this) {
    RegexPlacement.MarkdownDisplay -> stringResource(R.string.regex_placement_display)
    RegexPlacement.UserInput -> stringResource(R.string.regex_placement_user)
    RegexPlacement.AiResponse -> stringResource(R.string.regex_placement_ai)
    RegexPlacement.SlashCommand -> stringResource(R.string.regex_placement_slash)
    RegexPlacement.WorldInfo -> stringResource(R.string.regex_placement_world_info)
    RegexPlacement.Reasoning -> stringResource(R.string.regex_placement_reasoning)
}

@Preview(showBackground = true)
@Composable
private fun RegexScriptLayoutPreview() {
    AppTheme(dynamicColor = false) {
        RegexScriptLayout(
            RegexScriptUiState.Normal(
                scripts = listOf(
                    RegexScript(
                        id = "1",
                        scriptName = "Hide tags",
                        findRegex = "/<tag>.*?<\\/tag>/gis",
                        replaceString = "",
                        placement = listOf(RegexPlacement.AiResponse.value)
                    )
                )
            )
        )
    }
}
