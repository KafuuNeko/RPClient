package me.kafuuneko.rpclient.ui.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.ui.dialog.AppInputDialog
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField

/**
 * 现代流式标签/关键词胶囊输入组件。
 *
 * 支持快速添加、逗号/换行批量添加、点选修改以及一键移除标签。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RpChipInputField(
    title: String,
    chips: List<String>,
    onChipsChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    addLabel: String = stringResource(R.string.add),
    placeholder: String = stringResource(R.string.tag_input_placeholder),
    editDialogTitle: String = stringResource(R.string.edit_tag_title),
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val cleanChips = remember(chips) { chips.filter { text -> text.isNotBlank() } }
    var isInputting by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var editingChipIndex by remember { mutableStateOf<Int?>(value = null) }

    fun commitCurrentInput() {
        val merged = mergeChips(cleanChips, inputText)
        if (merged != cleanChips) onChipsChanged(merged)
        inputText = ""
        isInputting = false
    }

    RpSettingsGroup(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChipInputHeader(
                title = title,
                subtitle = subtitle,
                icon = icon,
                count = cleanChips.size,
                accentColor = accentColor,
            )
            ChipFlow(
                chips = cleanChips,
                enabled = enabled,
                inputVisible = isInputting,
                addLabel = addLabel,
                accentColor = accentColor,
                onEdit = { value -> editingChipIndex = value },
                onRemove = { index -> onChipsChanged(cleanChips.filterIndexed { i, _ -> i != index }) },
                onAdd = { isInputting = true },
            )
            InlineChipInput(
                visible = isInputting,
                value = inputText,
                placeholder = placeholder,
                accentColor = accentColor,
                onValueChange = { value ->
                    if (value.endsWith(",") || value.endsWith("，") || value.endsWith("\n")) {
                        onChipsChanged(mergeChips(cleanChips, value))
                        inputText = ""
                    } else {
                        inputText = value
                    }
                },
                onDone = ::commitCurrentInput,
            )
        }
    }
    ChipEditDialog(
        index = editingChipIndex,
        chips = cleanChips,
        title = editDialogTitle,
        onDismiss = { editingChipIndex = null },
        onChipsChanged = onChipsChanged,
    )
}

@Composable
private fun ChipInputHeader(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    count: Int,
    accentColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            RpIconBubble(
                icon = icon,
                containerColor = accentColor.copy(alpha = 0.12f),
                contentColor = accentColor,
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = 0.10f),
            border = BorderStroke(width = 0.5.dp, color = accentColor.copy(alpha = 0.20f)),
        ) {
            Text(
                text = stringResource(R.string.chip_count, count),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    chips: List<String>,
    enabled: Boolean,
    inputVisible: Boolean,
    addLabel: String,
    accentColor: Color,
    onEdit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEachIndexed { index, chipText ->
            EditableInputChip(
                text = chipText,
                enabled = enabled,
                accentColor = accentColor,
                onEdit = { onEdit(index) },
                onRemove = { onRemove(index) },
            )
        }
        if (enabled && !inputVisible) {
            AddInputChip(addLabel, onAdd)
        }
    }
}

@Composable
private fun EditableInputChip(
    text: String,
    enabled: Boolean,
    accentColor: Color,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = { if (enabled) onEdit() },
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingIcon = if (enabled) {
            {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onRemove),
                )
            }
        } else null,
        shape = RoundedCornerShape(10.dp),
        colors = InputChipDefaults.inputChipColors(
            containerColor = accentColor.copy(alpha = 0.08f),
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = InputChipDefaults.inputChipBorder(
            enabled = enabled,
            selected = false,
            borderColor = accentColor.copy(alpha = 0.22f),
            borderWidth = 0.5.dp,
        ),
    )
}

@Composable
private fun AddInputChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = label,
                modifier = Modifier.size(16.dp)
            )
        },
        shape = RoundedCornerShape(10.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            borderWidth = 0.5.dp,
        ),
    )
}

@Composable
private fun InlineChipInput(
    visible: Boolean,
    value: String,
    placeholder: String,
    accentColor: Color,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f),
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                trailingIcon = {
                    if (value.isNotBlank()) {
                        IconButton(onClick = onDone) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = stringResource(R.string.confirm),
                                tint = accentColor,
                            )
                        }
                    }
                },
            )
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ChipEditDialog(
    index: Int?,
    chips: List<String>,
    title: String,
    onDismiss: () -> Unit,
    onChipsChanged: (List<String>) -> Unit,
) {
    var draft by remember(index) {
        mutableStateOf(index?.let { value -> chips.getOrNull(value) }.orEmpty())
    }
    if (index == null || index !in chips.indices) return
    AppInputDialog(
        onDismissRequest = onDismiss,
        title = title,
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        onConfirm = {
            val updated = chips.toMutableList()
            if (draft.isBlank()) updated.removeAt(index) else updated[index] = draft.trim()
            onChipsChanged(updated)
            onDismiss()
        },
    )
}

private fun mergeChips(existing: List<String>, input: String): List<String> {
    val additions = input
        .split(Regex("[,，\\n]+"))
        .map(String::trim)
        .filter(String::isNotBlank)
    return (existing + additions).distinct()
}
