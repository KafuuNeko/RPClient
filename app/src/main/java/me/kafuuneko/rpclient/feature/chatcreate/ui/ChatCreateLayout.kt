package me.kafuuneko.rpclient.feature.chatcreate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateForm
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookEntryItem
import me.kafuuneko.rpclient.feature.chatcreate.model.ChatCreateLorebookGroupItem
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateDialogState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateLoadState
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiIntent
import me.kafuuneko.rpclient.feature.chatcreate.presentation.ChatCreateUiState
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialog
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialogItem
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerMode
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCreationBottomBar
import me.kafuuneko.rpclient.ui.widgets.RpCreationSwitchCard
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.RpTagRow
import me.kafuuneko.rpclient.ui.widgets.UserPersonaPicker
import me.kafuuneko.rpclient.ui.widgets.WorldBookSelectionSummaryCard
import me.kafuuneko.rpclient.ui.model.WorldBookSelectionSummaryItem
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground

/** 新建单角色会话页 Compose 入口。 */
@Composable
fun ChatCreateLayout(
    uiState: ChatCreateUiState,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    BackHandler(
        enabled = uiState is ChatCreateUiState.Normal,
        onBack = { ChatCreateUiIntent.Back.emit() },
    )
    when (uiState) {
        ChatCreateUiState.None -> Unit
        is ChatCreateUiState.Finished -> ChatCreateLayout(uiState = uiState.previous, emit = {})
        is ChatCreateUiState.Normal -> {
            ChatCreateNormal(state = uiState, emit = emit)
            DialogSwitch(state = uiState, emit = emit)
        }
    }
}

@Composable
private fun ChatCreateNormal(
    state: ChatCreateUiState.Normal,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    val controlsEnabled = state.loadState == ChatCreateLoadState.None
    val selectedCharacter = state.selectedCharacter

    AppPageScaffold(
        // 缩小整个表单视口，底栏与输入区共同避让键盘
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.create_chat_title),
                onBack = { ChatCreateUiIntent.Back.emit() },
            )
        },
        bottomBar = {
            CreateBottomBar(
                loadState = state.loadState,
                characterName = selectedCharacter?.name,
                selectedEntryCount = state.form.selectedLorebookEntryIds.size,
                emit = emit,
            )
        },
        content = { padding ->
            RpLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    top = 14.dp,
                    end = 18.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.loadState == ChatCreateLoadState.Loading) {
                    item { LoadingRow() }
                } else {
                    // 基础表单与用户人设区
                    item {
                        BasicForm(
                            form = state.form,
                            showCharacterExamplesSwitch = state.showCharacterExamplesSwitch,
                            emit = emit,
                        )
                    }
                    item {
                        UserPersonaPicker(
                            state = state.persona,
                            enabled = controlsEnabled,
                            onSelect = { ChatCreateUiIntent.SelectPersona(it).emit() },
                        )
                    }
                    chatCharacterSummaryItems(
                        state = state,
                        controlsEnabled = controlsEnabled,
                        emit = emit
                    )
                    chatOpeningPreviewItems(state = state, emit = emit)
                    // 世界书摘要区：展示已启用计数与活跃书目概览，点击打开检索选择器
                    item {
                        RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                    }
                    item {
                        WorldBookSelectionSummaryCard(
                            totalGroupCount = state.lorebookGroups.size,
                            selectedEntryCount = state.form.selectedLorebookEntryIds.size,
                            selectedGroups = state.selectedWorldBookSummaries,
                            enabled = controlsEnabled,
                            onClick = { ChatCreateUiIntent.ShowLorebookPicker.emit() },
                        )
                    }
                }
            }
        },
    )
}

/** 在已选角色下方展示可用开场白及当前选择。 */
private fun LazyListScope.chatOpeningPreviewItems(
    state: ChatCreateUiState.Normal,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    // 角色开场白紧随角色摘要展示，切换角色后可立即预览并选择
    if (state.selectedCharacterFirstMessages.isNotEmpty()) {
        item {
            RpSectionHeader(title = stringResource(R.string.first_messages))
        }
        itemsIndexed(state.selectedCharacterFirstMessages) { index, message ->
            FirstMessageOption(
                message = message,
                selected = state.form.selectedFirstMessageIndex == index,
                onClick = { ChatCreateUiIntent.SelectFirstMessage(index).emit() },
            )
        }
    }
}

/** 单聊主表单的角色选择入口与已选摘要。 */
private fun LazyListScope.chatCharacterSummaryItems(
    state: ChatCreateUiState.Normal,
    controlsEnabled: Boolean,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    val selectedCharacter = state.selectedCharacter
    // 角色绑定摘要区：仅展示已选角色，海量角色切换交由独立检索弹窗完成
    item {
        RpSectionHeader(
            title = stringResource(R.string.bind_character),
            action = if (state.characters.isNotEmpty() && controlsEnabled) {
                stringResource(
                    if (selectedCharacter != null) R.string.picker_change
                    else R.string.picker_choose,
                )
            } else {
                null
            },
            onAction = { ChatCreateUiIntent.ShowCharacterPicker.emit() },
        )
    }
    item {
        when {
            state.characters.isEmpty() -> EmptyCard(
                icon = Icons.Rounded.Person,
                text = stringResource(R.string.no_characters_for_chat),
            )

            selectedCharacter == null -> CharacterSelectionPromptCard(
                totalCount = state.characters.size,
                enabled = controlsEnabled,
                onClick = { ChatCreateUiIntent.ShowCharacterPicker.emit() },
            )

            else -> SelectedCharacterSummaryCard(
                character = selectedCharacter,
                avatarImage = state.characterAvatars[selectedCharacter.id],
                enabled = controlsEnabled,
                onClick = { ChatCreateUiIntent.ShowCharacterPicker.emit() },
            )
        }
    }
}

@Composable
private fun DialogSwitch(
    state: ChatCreateUiState.Normal,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    // 统一由状态树分发角色选择器与世界书选择器弹窗
    when (val dialogState = state.dialogState) {
        ChatCreateDialogState.None -> Unit

        is ChatCreateDialogState.CharacterPicker -> {
            val visibleItems = remember(dialogState.visibleCharacters) {
                dialogState.visibleCharacters.map { character ->
                    CharacterPickerDialogItem(
                        id = character.id,
                        name = character.name,
                        description = character.description,
                        tags = character.tags,
                    )
                }
            }
            CharacterPickerDialog(
                title = stringResource(R.string.picker_select_character_title),
                mode = CharacterPickerMode.Single,
                totalCount = state.characters.size,
                visibleCharacters = visibleItems,
                characterAvatars = state.characterAvatars,
                selectedIds = state.form.selectedCharacterId?.let { id -> setOf(id) } ?: emptySet(),
                query = dialogState.query,
                emptyText = stringResource(R.string.no_characters_for_chat),
                onQueryChange = { text -> ChatCreateUiIntent.ChangeCharacterQuery(text).emit() },
                onVisibleCharactersChanged = { visibleIds, targetSizePx ->
                    ChatCreateUiIntent.VisiblePickerCharactersChanged(
                        characterIds = visibleIds,
                        targetSizePx = targetSizePx,
                    ).emit()
                },
                onCharacterClick = { id -> ChatCreateUiIntent.SelectCharacter(id).emit() },
                onDismissRequest = { ChatCreateUiIntent.DismissDialog.emit() },
            )
        }

        is ChatCreateDialogState.LorebookPicker -> {
            val allDialogGroups = remember(state.lorebookGroups) {
                state.lorebookGroups.map(ChatCreateLorebookGroupItem::toSessionLorebookDialogGroup)
            }
            val visibleDialogGroups = remember(dialogState.visibleGroups) {
                dialogState.visibleGroups.map(
                    ChatCreateLorebookGroupItem::toSessionLorebookDialogGroup,
                )
            }
            SessionLorebookDialog(
                groups = allDialogGroups,
                visibleGroups = visibleDialogGroups,
                query = dialogState.query,
                enabledEntryIds = dialogState.enabledEntryIds,
                title = stringResource(R.string.enabled_world_book_entries),
                showSelectedOnly = dialogState.showSelectedOnly,
                onQueryChange = { text -> ChatCreateUiIntent.ChangeLorebookQuery(text).emit() },
                onShowSelectedOnlyChange = { selectedOnly ->
                    ChatCreateUiIntent.ToggleLorebookPickerSelectedOnly(selectedOnly).emit()
                },
                onToggleGroup = { id -> ChatCreateUiIntent.ToggleLorebook(id).emit() },
                onToggleEntry = { id -> ChatCreateUiIntent.ToggleLorebookEntry(id).emit() },
                onConfirmSelection = { ChatCreateUiIntent.ConfirmLorebookPicker.emit() },
                onDismissRequest = { ChatCreateUiIntent.DismissDialog.emit() },
            )
        }
    }
}

/** 将新建单聊页的世界书分组转换为通用选择器弹窗展示模型。 */
private fun ChatCreateLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
    return SessionLorebookDialogGroup(
        id = lorebookId,
        name = lorebookName,
        entries = entries.map { entry ->
            SessionLorebookDialogEntry(
                id = entry.id,
                name = entry.name,
                content = entry.content,
                keywords = entry.keywords,
                constant = entry.constant,
            )
        },
    )
}

@Composable
private fun BasicForm(
    form: ChatCreateForm,
    showCharacterExamplesSwitch: Boolean,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    // 创建页只维护表单草稿，提交时才将这些设置写入会话
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .generalContentBackground(shape = RoundedCornerShape(12.dp), hasLabel = true),
            value = form.title,
            onValueChange = { text -> ChatCreateUiIntent.ChangeTitle(text).emit() },
            label = { Text(stringResource(R.string.chat_title)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AddComment,
                    contentDescription = null,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        RpScrollableOutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = form.userNote,
            onValueChange = { text -> ChatCreateUiIntent.ChangeUserNote(text).emit() },
            label = { Text(stringResource(R.string.default_user_note)) },
            minLines = 3,
            maxLines = 8,
            shape = RoundedCornerShape(12.dp),
        )
        // 示例开关与角色选择独立，切换角色时继续保留用户的选择
        if (showCharacterExamplesSwitch) {
            RpCreationSwitchCard(
                title = stringResource(R.string.chat_include_character_examples),
                icon = Icons.Rounded.FormatQuote,
                checked = form.includeCharacterExamples,
                onCheckedChange = { enabled ->
                    ChatCreateUiIntent.ToggleIncludeCharacterExamples(enabled).emit()
                },
            )
        }
    }
}

/** 展示当前已绑定角色的完整摘要卡片，点击可直接打开角色选择器切换。 */
@Composable
private fun SelectedCharacterSummaryCard(
    character: ChatCreateCharacterItem,
    avatarImage: ImageBitmap?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = getMacaronColor(character.name)
    // 通过描边强调已绑定角色，整卡点击拉起单选检索弹窗替换角色
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpAvatar(
                text = character.name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                image = avatarImage,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = character.description.ifBlank { stringResource(R.string.no_description) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (character.tags.isNotEmpty()) {
                    RpTagRow(tags = character.tags, maxCount = 4)
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 尚未选择角色时的引导摘要卡片，点击拉起角色选择器。 */
@Composable
private fun CharacterSelectionPromptCard(
    totalCount: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 角色已被删除或未预选时保持明确入口，提示总可选数量并引导点击检索
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon = Icons.Rounded.Person)
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.character_editor_empty),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.picker_character_tap_to_choose, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FirstMessageOption(
    message: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 整张开场白卡片接收选择操作，长文本仍可在原位置对比。
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(modifier = Modifier.width(8.dp))
            RpIconBubble(Icons.Rounded.FormatQuote)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CreateBottomBar(
    loadState: ChatCreateLoadState,
    characterName: String?,
    selectedEntryCount: Int,
    emit: ChatCreateUiIntent.() -> Unit,
) {
    RpCreationBottomBar(
        title = characterName ?: stringResource(R.string.character_editor_empty),
        subtitle = stringResource(R.string.world_books_enabled, selectedEntryCount),
        enabled = characterName != null && loadState == ChatCreateLoadState.None,
        creating = loadState == ChatCreateLoadState.Creating,
        onCreate = { ChatCreateUiIntent.CreateChat.emit() },
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyCard(
    icon: ImageVector,
    text: String,
) {
    AppCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ChatCreateLayoutPreview() {
    // 预览提供与真实表单一致的已选角色和世界书摘要。
    val character = ChatCreateCharacterItem(
        id = 1L,
        name = "Lyra",
        tags = listOf("Mystery"),
        description = "Archivist",
    )
    AppTheme(dynamicColor = false) {
        ChatCreateLayout(
            uiState = ChatCreateUiState.Normal(
                form = ChatCreateForm(
                    selectedCharacterId = 1L,
                    selectedFirstMessageIndex = 0,
                    selectedLorebookEntryIds = setOf(1L),
                ),
                selectedCharacterFirstMessages = listOf(
                    "The rain taps gently against the window as Lyra opens the case file.",
                    "You arrive at the archive just before midnight.",
                ),
                characters = listOf(character),
                selectedCharacter = character,
                selectedWorldBookSummaries = listOf(
                    WorldBookSelectionSummaryItem(
                        lorebookId = 1L,
                        name = "Fog Harbor",
                        selectedCount = 1,
                        totalCount = 1,
                    ),
                ),
                lorebookGroups = listOf(
                    element = ChatCreateLorebookGroupItem(
                        lorebookId = 1L,
                        lorebookName = "Fog Harbor",
                        entryCount = 1,
                        entries = listOf(
                            element = ChatCreateLorebookEntryItem(
                                id = 1L,
                                lorebookName = "Fog Harbor",
                                name = "Old Town",
                                keywords = emptyList(),
                                secondaryKeywords = emptyList(),
                                order = 100,
                                depth = 0,
                                category = emptyList(),
                                content = "Persistent lore content.",
                                constant = false,
                            ),
                        ),
                    ),
                ),
            ),
            emit = {},
        )
    }
}
