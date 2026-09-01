package me.kafuuneko.rpclient.feature.main.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.main.model.MainImportCharacterItem
import me.kafuuneko.rpclient.feature.main.presentation.MainChatDataManagementState
import me.kafuuneko.rpclient.feature.main.presentation.MainDialogState
import me.kafuuneko.rpclient.feature.main.presentation.MainUiIntent
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpSettingsGroup
import me.kafuuneko.rpclient.ui.widgets.RpSettingsTile

/** 设置页中的对话文件导入入口。 */
@Composable
internal fun ChatDataManagementPanel(
    state: MainChatDataManagementState,
    emit: MainUiIntent.() -> Unit
) {
    val isReading = state == MainChatDataManagementState.Reading
    RpSettingsGroup {
        RpSettingsTile(
            icon = Icons.Rounded.FileDownload,
            title = stringResource(R.string.import_chat),
            subtitle = if (isReading) {
                stringResource(R.string.reading_chat_file)
            } else {
                stringResource(R.string.import_chat_desc)
            },
            enabled = !isReading,
            onClick = { MainUiIntent.ImportChatClick.emit() },
            trailing = {
                if (isReading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        )
    }
}

/** 解析成功后强制用户确认目标角色卡的导入对话框。 */
@Composable
internal fun ImportChatCharacterDialog(
    state: MainDialogState.ImportChatCharacterSelection,
    emit: MainUiIntent.() -> Unit
) {
    val canDismiss = !state.isImporting
    AppDialogScaffold(
        onDismissRequest = {
            if (canDismiss) MainUiIntent.DismissDialog.emit()
        },
        title = stringResource(R.string.select_import_character_title),
        badgeIcon = Icons.Rounded.FileDownload,
        badgeTone = DialogBadgeTone.Primary,
        confirmText = stringResource(
            if (state.isImporting) R.string.importing_chat else R.string.import_chat
        ),
        dismissText = stringResource(R.string.cancel),
        confirmEnabled = state.selectedCharacterId != null && !state.isImporting,
        isConfirmLoading = state.isImporting,
        onConfirm = { MainUiIntent.ConfirmImportChat.emit() },
        onDismiss = {
            if (canDismiss) MainUiIntent.DismissDialog.emit()
        }
    ) {
        ImportCharacterSelectionContent(state, emit)
    }
}

@Composable
private fun ImportCharacterSelectionContent(
    state: MainDialogState.ImportChatCharacterSelection,
    emit: MainUiIntent.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ImportPreview(state)
        if (state.characters.isEmpty()) {
            Text(
                text = stringResource(R.string.no_characters_for_chat_import),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = { MainUiIntent.ChangeImportCharacterQuery(it).emit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isImporting,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_characters),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        if (state.visibleCharacters.isEmpty()) {
            Text(
                text = stringResource(R.string.no_matching_characters),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            RpLazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.visibleCharacters, key = { it.id }) { character ->
                    ImportCharacterItem(
                        character = character,
                        selected = character.id == state.selectedCharacterId,
                        enabled = !state.isImporting,
                        onClick = {
                            MainUiIntent.SelectImportCharacter(character.id).emit()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportPreview(state: MainDialogState.ImportChatCharacterSelection) {
    val sourceCharacterName = if (state.sourceCharacterName.isBlank()) {
        stringResource(R.string.unknown_character)
    } else {
        state.sourceCharacterName
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(
                R.string.import_chat_source_character,
                sourceCharacterName
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pluralStringResource(
                R.plurals.import_chat_message_count,
                state.messageCount,
                state.messageCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportCharacterItem(
    character: MainImportCharacterItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, enabled = enabled, onClick = onClick)
            RpAvatar(
                text = character.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                color = getMacaronColor(character.name.ifBlank { "character" }),
                modifier = Modifier.size(38.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (character.details.isNotBlank()) {
                    Text(
                        text = character.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ImportChatCharacterDialogPreview() {
    val characters = listOf(
        MainImportCharacterItem(1L, "Seraphina", "Example creator"),
        MainImportCharacterItem(2L, "Rowan", "An impulsive explorer")
    )
    AppTheme(dynamicColor = false) {
        ImportChatCharacterDialog(
            state = MainDialogState.ImportChatCharacterSelection(
                title = "Imported conversation",
                sourceCharacterName = "Seraphina",
                messageCount = 42,
                query = "",
                characters = characters,
                visibleCharacters = characters,
                selectedCharacterId = 1L
            ),
            emit = {}
        )
    }
}
