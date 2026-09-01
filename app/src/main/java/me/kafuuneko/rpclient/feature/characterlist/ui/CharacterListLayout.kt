package me.kafuuneko.rpclient.feature.characterlist.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.characterlist.model.CharacterListItem
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterImportStage
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListDialogState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListLoadState
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiIntent
import me.kafuuneko.rpclient.feature.characterlist.presentation.CharacterListUiState
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.dialog.AppWarningDialog
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.CharacterAccentColors
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpPageTitle
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow

/** 角色列表页 Compose 入口，包含搜索、选择及导入导出操作。 */
@Composable
fun CharacterListLayout(
    uiState: CharacterListUiState,
    emit: CharacterListUiIntent.() -> Unit
) {
    BackHandler(enabled = uiState is CharacterListUiState.Normal) { CharacterListUiIntent.Back.emit() }
    when (uiState) {
        CharacterListUiState.None -> Unit
        is CharacterListUiState.Finished -> CharacterListLayout(uiState.previous) {}
        is CharacterListUiState.Normal -> CharacterListNormal(uiState, emit)
    }
}

@Composable
private fun CharacterListNormal(
    state: CharacterListUiState.Normal,
    emit: CharacterListUiIntent.() -> Unit
) {
    val listState = rememberLazyListState()
    val avatarSizePx = with(LocalDensity.current) { 54.dp.roundToPx() }
    LaunchedEffect(listState, state.characters, avatarSizePx) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { it.key as? Long }
                .toSet()
        }
            .distinctUntilChanged()
            .collect { visibleIds ->
                CharacterListUiIntent.VisibleCharactersChanged(
                    characterIds = visibleIds,
                    targetSizePx = avatarSizePx
                ).emit()
            }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = stringResource(R.string.character_manager),
            onBack = { CharacterListUiIntent.Back.emit() },
            actions = {
                IconButton(onClick = { CharacterListUiIntent.ImportCharacterClick.emit() }) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.import_character))
                }
                IconButton(onClick = { CharacterListUiIntent.CreateCharacter.emit() }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_character))
                }
            }
        )
        RpLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 8.dp,
                end = 18.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SearchField(state.searchText, emit) }
            item {
                RpSectionHeader(
                    title = stringResource(R.string.all_characters),
                    action = stringResource(R.string.create),
                    onAction = { CharacterListUiIntent.CreateCharacter.emit() }
                )
            }
            if (state.loadState != CharacterListLoadState.None) {
                item { LoadingRow(state.loadState) }
            }
            val characters = state.characters
            if (state.loadState != CharacterListLoadState.Loading && characters.isEmpty()) {
                item { EmptyCharacterCard(emit) }
            }
            items(characters, key = { it.id }) { character ->
                CharacterListCard(
                    character = character,
                    selected = character.id == state.selectedCharacterId,
                    onClick = { CharacterListUiIntent.SelectCharacter(character.id).emit() },
                    onExport = { CharacterListUiIntent.ExportCharacterJsonClick(character.id).emit() }
                )
            }
        }
    }
    DialogSwitch(state.dialogState, emit)
}

@Composable
private fun DialogSwitch(
    dialogState: CharacterListDialogState,
    emit: CharacterListUiIntent.() -> Unit
) {
    // 低预算确认在单卡与批量场景复用同一策略入口
    when (dialogState) {
        CharacterListDialogState.None -> Unit
        is CharacterListDialogState.LowEmbeddedLorebookBudgetConfirm -> AppWarningDialog(
            onDismissRequest = {
                CharacterListUiIntent.ImportCharacterWithOriginalLorebookBudget.emit()
            },
            title = stringResource(R.string.low_world_book_budget_title),
            message = if (dialogState.affectedCharacterCount == 1) {
                stringResource(
                    R.string.low_world_book_budget_message,
                    dialogState.importedTokenBudget
                )
            } else {
                stringResource(
                    R.string.batch_low_world_book_budget_message,
                    dialogState.affectedCharacterCount,
                    dialogState.importedTokenBudget
                )
            },
            confirmText = stringResource(R.string.follow_global_budget),
            dismissText = stringResource(R.string.keep_imported_budget),
            onConfirm = {
                CharacterListUiIntent.ImportCharacterWithGlobalLorebookBudget.emit()
            }
        )
        // 结果统计属于可恢复页面状态，不使用易丢失的瞬时 Toast
        is CharacterListDialogState.BatchImportResult -> AppDialogScaffold(
            onDismissRequest = { CharacterListUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.batch_import_character_result_title),
            confirmText = stringResource(android.R.string.ok),
            dismissText = null,
            onConfirm = { CharacterListUiIntent.DismissDialog.emit() }
        ) {
            Text(
                stringResource(
                    R.string.batch_import_character_result_message,
                    dialogState.successCount,
                    dialogState.failureCount
                )
            )
        }
    }
}

@Composable
private fun SearchField(
    searchText: String,
    emit: CharacterListUiIntent.() -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = searchText,
        onValueChange = { CharacterListUiIntent.ChangeSearchText(it).emit() },
        placeholder = {
            Text(
                text = stringResource(R.string.search_placeholder),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (searchText.isNotEmpty()) {
                IconButton(onClick = { CharacterListUiIntent.ChangeSearchText("").emit() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun LoadingRow(loadState: CharacterListLoadState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator()
        // 普通列表加载与导出保持简洁，只有批量导入显示阶段计数
        if (loadState is CharacterListLoadState.Importing) {
            val messageRes = when (loadState.stage) {
                CharacterImportStage.Reading -> R.string.batch_import_character_reading_progress
                CharacterImportStage.Saving -> R.string.batch_import_character_saving_progress
            }
            Text(
                text = stringResource(
                    messageRes,
                    loadState.completedCount,
                    loadState.totalCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyCharacterCard(emit: CharacterListUiIntent.() -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RpIconBubble(Icons.Rounded.Person)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    stringResource(R.string.no_character_cards),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.no_character_cards_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                )
            }
            Button(
                onClick = { CharacterListUiIntent.CreateCharacter.emit() },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.create))
            }
        }
    }
}

@Composable
private fun CharacterListCard(
    character: CharacterListItem,
    selected: Boolean,
    onClick: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarPreview(
                avatarText = character.avatarText,
                avatarColor = character.avatarColor,
                image = character.avatarImage,
                size = 58
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = character.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        IconButton(
                            onClick = onExport,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.export_character),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Text(
                    character.description.ifBlank { stringResource(R.string.no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RpTagRow(character.tags, maxCount = 4)
            }
        }
    }
}

@Composable
private fun AvatarPreview(
    avatarText: String,
    avatarColor: Color,
    image: ImageBitmap?,
    size: Int
) {
    Surface(
        modifier = Modifier.size(size.dp),
        shape = RoundedCornerShape(16.dp),
        color = avatarColor.copy(alpha = 0.14f),
        border = BorderStroke(0.5.dp, avatarColor.copy(alpha = 0.30f))
    ) {
        if (image == null) {
            RpAvatar(
                text = avatarText,
                color = avatarColor,
                modifier = Modifier.size(size.dp),
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .size(size.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun CharacterListLayoutPreview() {
    AppTheme(dynamicColor = false) {
        CharacterListLayout(
            uiState = CharacterListUiState.Normal(
                selectedCharacterId = 1L,
                characters = listOf(
                    CharacterListItem(
                        id = 1L,
                        name = "Character",
                        tags = listOf("Tag"),
                        description = "Description",
                        avatarText = "C",
                        avatarColor = CharacterAccentColors.first()
                    )
                )
            ),
            emit = {}
        )
    }
}
