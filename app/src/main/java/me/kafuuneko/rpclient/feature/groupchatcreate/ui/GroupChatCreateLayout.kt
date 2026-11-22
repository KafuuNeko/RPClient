package me.kafuuneko.rpclient.feature.groupchatcreate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatCreateGreetingState
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingCharacterItem
import me.kafuuneko.rpclient.feature.groupchatcreate.model.GroupChatGreetingMode
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateDialogState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateLoadState
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiIntent
import me.kafuuneko.rpclient.feature.groupchatcreate.presentation.GroupChatCreateUiState
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialog
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialogItem
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerMode
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.getMacaronColor
import me.kafuuneko.rpclient.ui.widgets.AppCard
import me.kafuuneko.rpclient.ui.widgets.AppPageScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip as FilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField as OutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpAvatar
import me.kafuuneko.rpclient.ui.widgets.RpCreationBottomBar
import me.kafuuneko.rpclient.ui.widgets.RpCreationSwitchCard
import me.kafuuneko.rpclient.ui.widgets.RpIconBubble
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.RpSectionHeader
import me.kafuuneko.rpclient.ui.widgets.UserPersonaPicker
import me.kafuuneko.rpclient.ui.widgets.WorldBookSelectionSummaryCard
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors

/** 新建群聊页 Compose 入口，负责成员编排、开场白与世界书授权交互。 */
@Composable
fun GroupChatCreateLayout(
    uiState: GroupChatCreateUiState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit = {},
) {
    BackHandler(
        enabled = uiState is GroupChatCreateUiState.Normal,
        onBack = { emitIntent(GroupChatCreateUiIntent.Back) },
    )
    when (uiState) {
        GroupChatCreateUiState.None -> Unit
        is GroupChatCreateUiState.Finished -> GroupChatCreateLayout(uiState.previous) {}

        is GroupChatCreateUiState.Normal -> {
            GroupChatCreateNormalView(state = uiState, emitIntent = emitIntent)
            DialogSwitch(state = uiState, emitIntent = emitIntent)
        }
    }
}

@Composable
private fun GroupChatCreateNormalView(
    state: GroupChatCreateUiState.Normal,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    val controlsEnabled = state.loadState == GroupChatCreateLoadState.None
    AppPageScaffold(
        // 缩小整个表单视口，底栏与输入区共同避让键盘
        modifier = Modifier.imePadding(),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.group_chat_create_title),
                onBack = { emitIntent(GroupChatCreateUiIntent.Back) },
            )
        },
        bottomBar = {
            CreateBottomBar(
                selectedCount = state.selectedCount,
                subtitleResId = state.creationStatusResId,
                enabled = state.canCreate && controlsEnabled,
                onCreate = { emitIntent(GroupChatCreateUiIntent.Create) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                groupMemberSetupItems(
                    state = state,
                    controlsEnabled = controlsEnabled,
                    emitIntent = emitIntent
                )
                groupPromptItems(state = state, emitIntent = emitIntent)
                // 世界书摘要区：点击打开独立检索选择器弹窗
                item {
                    RpSectionHeader(title = stringResource(R.string.enabled_world_book_entries))
                }
                item {
                    WorldBookSelectionSummaryCard(
                        totalGroupCount = state.lorebookGroups.size,
                        selectedEntryCount = state.selectedLorebookEntryIds.size,
                        selectedGroups = state.selectedWorldBookSummaries,
                        enabled = controlsEnabled,
                        onClick = { emitIntent(GroupChatCreateUiIntent.ShowLorebookPicker) },
                    )
                }
            }
        },
    )
}

/** 群聊主表单中的轮转、开场白以及角色卡示例配置。 */
private fun LazyListScope.groupPromptItems(
    state: GroupChatCreateUiState.Normal,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    // 发言轮转与开场白配置区
    item {
        StrategySection(
            selected = state.activationStrategy,
            onSelect = { strategy ->
                emitIntent(GroupChatCreateUiIntent.SelectStrategy(strategy))
            },
        )
    }
    item {
        RpCreationSwitchCard(
            title = stringResource(R.string.group_chat_allow_consecutive),
            subtitle = stringResource(R.string.group_chat_allow_consecutive_desc),
            icon = Icons.Rounded.AutoAwesome,
            checked = state.allowSelfResponses,
            onCheckedChange = { enabled ->
                emitIntent(GroupChatCreateUiIntent.ToggleAllowSelfResponses(enabled))
            },
        )
    }
    item {
        GreetingSection(
            state = state.greetingState,
            emitIntent = emitIntent,
        )
    }
    if (state.showCharacterExamplesSwitch) {
        item {
            RpCreationSwitchCard(
                title = stringResource(R.string.chat_include_character_examples),
                icon = Icons.Rounded.FormatQuote,
                checked = state.includeCharacterExamples,
                onCheckedChange = { enabled ->
                    emitIntent(
                        GroupChatCreateUiIntent.ToggleIncludeCharacterExamples(enabled),
                    )
                },
            )
        }
    }
}

/** 群聊基础信息、人设与成员摘要区域。 */
private fun LazyListScope.groupMemberSetupItems(
    state: GroupChatCreateUiState.Normal,
    controlsEnabled: Boolean,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    // 基础信息与人设配置
    item {
        OutlinedTextField(
            value = state.title,
            onValueChange = { text ->
                emitIntent(GroupChatCreateUiIntent.ChangeTitle(text))
            },
            modifier = Modifier
                .fillMaxWidth()
                .generalContentBackground(
                    shape = RoundedCornerShape(16.dp),
                    hasLabel = true,
                ),
            label = { Text(stringResource(R.string.group_chat_title_label)) },
            placeholder = {
                Text(stringResource(R.string.group_chat_title_placeholder))
            },
            enabled = controlsEnabled,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
    }
    item {
        UserPersonaPicker(
            state = state.persona,
            enabled = controlsEnabled,
            onSelect = { emitIntent(GroupChatCreateUiIntent.SelectPersona(it)) },
        )
    }
    // 群聊成员摘要区置于开场白之前，方便先确立参演阵容再配置手动或自定义开场白
    item {
        RpSectionHeader(
            title = stringResource(R.string.group_chat_members),
            action = if (state.characters.isNotEmpty() && controlsEnabled) {
                stringResource(
                    if (state.selectedCharacters.isNotEmpty()) R.string.manage
                    else R.string.picker_choose,
                )
            } else {
                null
            },
            onAction = { emitIntent(GroupChatCreateUiIntent.ShowCharacterPicker) },
        )
    }
    item {
        SelectedMembersSummaryCard(
            totalCharacterCount = state.characters.size,
            selectedCharacters = state.selectedCharacters,
            ready = state.canCreate,
            subtitleResId = state.creationStatusResId,
            characterAvatars = state.characterAvatars,
            enabled = controlsEnabled,
            onOpenPicker = { emitIntent(GroupChatCreateUiIntent.ShowCharacterPicker) },
            onRemoveMember = { characterId ->
                emitIntent(GroupChatCreateUiIntent.ToggleCharacter(characterId))
            },
        )
    }
}

/**
 * 展示群聊已选成员的紧凑摘要卡片，支持快速移除单个成员或拉起完整检索选择器。
 */
@Composable
private fun SelectedMembersSummaryCard(
    totalCharacterCount: Int,
    selectedCharacters: List<GroupChatCreateCharacterItem>,
    ready: Boolean,
    subtitleResId: Int,
    characterAvatars: Map<Long, ImageBitmap>,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onRemoveMember: (Long) -> Unit,
) {
    // 本地尚无任何角色卡时展示空态引导
    if (totalCharacterCount == 0) {
        NoMemberCandidatesCard()
        return
    }

    val selectedCount = selectedCharacters.size
    val titleText = if (selectedCount == 0) {
        stringResource(R.string.group_chat_members_empty_title)
    } else {
        pluralStringResource(R.plurals.group_chat_member_count, selectedCount, selectedCount)
    }
    val subtitleText = when {
        selectedCount == 0 -> stringResource(
            R.string.group_chat_members_empty_subtitle,
            totalCharacterCount,
        )

        else -> stringResource(subtitleResId)
    }
    val subtitleColor = if (selectedCount == 1) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (ready) 1.5.dp else 1.dp,
            color = if (ready) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SelectedMembersSummaryHeader(
                title = titleText,
                subtitle = subtitleText,
                subtitleColor = subtitleColor,
                enabled = enabled,
                onOpenPicker = onOpenPicker,
            )
            SelectedMemberChips(
                selectedCharacters = selectedCharacters,
                characterAvatars = characterAvatars,
                enabled = enabled,
                onRemoveMember = onRemoveMember,
            )
        }
    }
}


/** 展示已选成员及表单内的一键移除入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedMemberChips(
    selectedCharacters: List<GroupChatCreateCharacterItem>,
    characterAvatars: Map<Long, ImageBitmap>,
    enabled: Boolean,
    onRemoveMember: (Long) -> Unit,
) {
    // 以紧凑流式标签展示已选成员，支持在表单中直接移除成员
    if (selectedCharacters.isNotEmpty()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selectedCharacters.forEach { character ->
                SelectedMemberChip(
                    name = character.name,
                    avatarImage = characterAvatars[character.id],
                    enabled = enabled,
                    onRemove = { onRemoveMember(character.id) },
                )
            }
        }
    }
}

/** 没有可选角色时使用静态空态。 */
@Composable
private fun NoMemberCandidatesCard() {
    // 无可选角色时不提供打开选择器的入口。
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RpIconBubble(icon = Icons.Rounded.Groups)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.no_characters_for_chat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 显示已选成员计数和打开选择器的入口。 */
@Composable
private fun SelectedMembersSummaryHeader(
    title: String,
    subtitle: String,
    subtitleColor: Color,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
) {
    // 顶部摘要行负责拉起成员选择器弹窗
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpenPicker)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RpIconBubble(icon = Icons.Rounded.Groups)
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
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

/** 展示单个已选成员的紧凑胶囊标签，附带头像与一键移除图标。 */
@Composable
private fun SelectedMemberChip(
    name: String,
    avatarImage: ImageBitmap?,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val accent = getMacaronColor(name)
    // 限制名称最大宽度，确保小屏下多成员标签平稳换行且不挤压移除按钮
    AppSurface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 5.dp, end = 6.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RpAvatar(
                text = name.firstOrNull()?.uppercase() ?: "?",
                color = accent,
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                image = avatarImage,
            )
            Text(
                text = name,
                modifier = Modifier.widthIn(max = 150.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.picker_remove_selection),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrategySection(
    selected: GroupChatActivationStrategy,
    onSelect: (GroupChatActivationStrategy) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_turn_strategy))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupChatActivationStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = selected == strategy,
                    onClick = { onSelect(strategy) },
                    label = {
                        Text(
                            text = stringResource(strategy.titleRes()),
                            maxLines = 1,
                        )
                    },
                    colors = pageFilterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }
        Text(
            text = stringResource(selected.descriptionRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GreetingSection(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RpSectionHeader(title = stringResource(R.string.group_chat_greeting_mode))
        GroupChatGreetingMode.entries.forEach { mode ->
            GreetingModeOption(
                mode = mode,
                selected = state.mode == mode,
                onClick = {
                    emitIntent(GroupChatCreateUiIntent.SelectGreetingMode(mode))
                },
            )
        }
        when (state.mode) {
            GroupChatGreetingMode.RandomPerCharacter,
            GroupChatGreetingMode.None -> Unit

            GroupChatGreetingMode.Manual -> ManualGreetingEditor(
                state = state,
                emitIntent = emitIntent,
            )

            GroupChatGreetingMode.Custom -> CustomGreetingEditor(
                state = state,
                emitIntent = emitIntent,
            )
        }
    }
}

@Composable
private fun GreetingModeOption(
    mode: GroupChatGreetingMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 选中状态由单选按钮表达，底板直接使用页面统一的不透明度与模糊配置
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(mode.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(mode.descriptionRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ManualGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    GreetingCharacterSelector(state, emitIntent)
    val selectedCharacter = state.selectedCharacter
    if (selectedCharacter == null) {
        GreetingHint(R.string.group_chat_greeting_select_members_first)
        return
    }
    if (selectedCharacter.greetings.isEmpty()) {
        GreetingHint(R.string.group_chat_greeting_character_has_none)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_chat_greeting_choose_message),
            style = MaterialTheme.typography.labelLarge,
        )
        selectedCharacter.greetingPreviews.forEachIndexed { index, greeting ->
            // 手动开场白与模式选项共用底色，切换选中项不会改变底板的实际不透明度
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                        },
                    ),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    RadioButton(
                        selected = state.selectedGreetingIndex == index,
                        onClick = {
                            emitIntent(GroupChatCreateUiIntent.SelectGreeting(index))
                        },
                    )
                    Text(
                        text = greeting,
                        modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomGreetingEditor(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    GreetingCharacterSelector(state, emitIntent)
    if (state.selectedCharacter == null) {
        GreetingHint(R.string.group_chat_greeting_select_members_first)
        return
    }
    RpScrollableOutlinedTextField(
        value = state.customGreeting,
        onValueChange = { emitIntent(GroupChatCreateUiIntent.ChangeCustomGreeting(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.group_chat_custom_greeting)) },
        placeholder = {
            Text(stringResource(R.string.group_chat_custom_greeting_placeholder))
        },
        minLines = 4,
        maxLines = 10,
        shape = RoundedCornerShape(16.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GreetingCharacterSelector(
    state: GroupChatCreateGreetingState,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    if (state.characters.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_chat_greeting_choose_character),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.characters.forEach { character ->
                FilterChip(
                    selected = state.selectedCharacterId == character.id,
                    onClick = {
                        emitIntent(GroupChatCreateUiIntent.SelectGreetingCharacter(character.id))
                    },
                    label = { Text(character.name) },
                )
            }
        }
    }
}

@Composable
private fun GreetingHint(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun CreateBottomBar(
    selectedCount: Int,
    subtitleResId: Int,
    enabled: Boolean,
    onCreate: () -> Unit,
) {
    RpCreationBottomBar(
        title = pluralStringResource(
            R.plurals.group_chat_member_count,
            selectedCount,
            selectedCount,
        ),
        subtitle = stringResource(subtitleResId),
        enabled = enabled,
        onCreate = onCreate,
    )
}

@Composable
private fun DialogSwitch(
    state: GroupChatCreateUiState.Normal,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    // 加载与创建期间优先遮罩表单，选择器只在就绪时显示。
    val progressMessage = when (state.loadState) {
        GroupChatCreateLoadState.Loading -> R.string.group_chat_loading_characters
        GroupChatCreateLoadState.Creating -> R.string.group_chat_creating
        GroupChatCreateLoadState.None -> null
    }
    if (progressMessage != null) {
        LoadingDialog(
            title = stringResource(R.string.group_chat_create_title),
            description = stringResource(progressMessage),
        )
        return
    }

    when (val dialogState = state.dialogState) {
        GroupChatCreateDialogState.None -> Unit

        is GroupChatCreateDialogState.CharacterPicker -> {
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
                title = stringResource(R.string.group_chat_select_members_title),
                mode = CharacterPickerMode.Multiple,
                totalCount = state.characters.size,
                visibleCharacters = visibleItems,
                characterAvatars = state.characterAvatars,
                selectedIds = dialogState.selectedCharacterIds,
                query = dialogState.query,
                showSelectedOnly = dialogState.showSelectedOnly,
                emptyText = stringResource(R.string.no_characters_for_chat),
                onQueryChange = { text ->
                    emitIntent(GroupChatCreateUiIntent.ChangeSearchQuery(text))
                },
                onShowSelectedOnlyChange = { selectedOnly ->
                    emitIntent(
                        GroupChatCreateUiIntent.ToggleCharacterPickerSelectedOnly(
                            selectedOnly
                        )
                    )
                },
                onVisibleCharactersChanged = { visibleIds, targetSizePx ->
                    emitIntent(
                        GroupChatCreateUiIntent.VisiblePickerCharactersChanged(
                            characterIds = visibleIds,
                            targetSizePx = targetSizePx,
                        ),
                    )
                },
                onCharacterClick = { id ->
                    emitIntent(GroupChatCreateUiIntent.ToggleCharacterPickerItem(id))
                },
                onConfirm = { emitIntent(GroupChatCreateUiIntent.ConfirmCharacterPicker) },
                onDismissRequest = { emitIntent(GroupChatCreateUiIntent.DismissDialog) },
            )
        }

        is GroupChatCreateDialogState.LorebookPicker -> GroupCreateLorebookPicker(
            groups = state.lorebookGroups,
            dialogState = dialogState,
            emitIntent = emitIntent,
        )
    }
}

/** 将群聊创建草稿适配为通用世界书选择器。 */
@Composable
private fun GroupCreateLorebookPicker(
    groups: List<GroupChatLorebookGroupItem>,
    dialogState: GroupChatCreateDialogState.LorebookPicker,
    emitIntent: (GroupChatCreateUiIntent) -> Unit,
) {
    // 完整分组用于整本切换，当前搜索结果只决定可见列表。
    val allDialogGroups = remember(groups) {
        groups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(
            GroupChatLorebookGroupItem::toSessionLorebookDialogGroup,
        )
    }
    SessionLorebookDialog(
        groups = allDialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        enabledEntryIds = dialogState.enabledEntryIds,
        title = stringResource(R.string.enabled_world_book_entries),
        showSelectedOnly = dialogState.showSelectedOnly,
        onQueryChange = { text ->
            emitIntent(GroupChatCreateUiIntent.ChangeLorebookQuery(text))
        },
        onShowSelectedOnlyChange = { selectedOnly ->
            emitIntent(GroupChatCreateUiIntent.ToggleLorebookPickerSelectedOnly(selectedOnly))
        },
        onToggleGroup = { id -> emitIntent(GroupChatCreateUiIntent.ToggleLorebook(id)) },
        onToggleEntry = { id ->
            emitIntent(GroupChatCreateUiIntent.ToggleLorebookEntry(id))
        },
        onConfirmSelection = { emitIntent(GroupChatCreateUiIntent.ConfirmLorebookPicker) },
        onDismissRequest = { emitIntent(GroupChatCreateUiIntent.DismissDialog) },
    )
}

/** 将群聊创建页的世界书分组转换为通用选择器弹窗展示模型。 */
private fun GroupChatLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
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

private fun GroupChatActivationStrategy.titleRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual -> R.string.group_chat_strategy_manual
        GroupChatActivationStrategy.Natural -> R.string.group_chat_strategy_natural
        GroupChatActivationStrategy.List -> R.string.group_chat_strategy_list
        GroupChatActivationStrategy.Pooled -> R.string.group_chat_strategy_pooled
    }
}

private fun GroupChatActivationStrategy.descriptionRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual ->
            R.string.group_chat_strategy_manual_desc

        GroupChatActivationStrategy.Natural ->
            R.string.group_chat_strategy_natural_desc

        GroupChatActivationStrategy.List ->
            R.string.group_chat_strategy_list_desc

        GroupChatActivationStrategy.Pooled ->
            R.string.group_chat_strategy_pooled_desc
    }
}

private fun GroupChatGreetingMode.titleRes(): Int {
    return when (this) {
        GroupChatGreetingMode.RandomPerCharacter ->
            R.string.group_chat_greeting_random

        GroupChatGreetingMode.Manual ->
            R.string.group_chat_greeting_manual

        GroupChatGreetingMode.Custom ->
            R.string.group_chat_greeting_custom

        GroupChatGreetingMode.None ->
            R.string.group_chat_greeting_none
    }
}

private fun GroupChatGreetingMode.descriptionRes(): Int {
    return when (this) {
        GroupChatGreetingMode.RandomPerCharacter ->
            R.string.group_chat_greeting_random_desc

        GroupChatGreetingMode.Manual ->
            R.string.group_chat_greeting_manual_desc

        GroupChatGreetingMode.Custom ->
            R.string.group_chat_greeting_custom_desc

        GroupChatGreetingMode.None ->
            R.string.group_chat_greeting_none_desc
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun GroupChatCreatePreview() {
    // 预览已满足最低成员数且手动开场白已就绪的表单。
    AppTheme(darkTheme = false, dynamicColor = false) {
        GroupChatCreateLayout(
            uiState = GroupChatCreateUiState.Normal(
                characters = previewCharacters,
                selectedCharacters = previewCharacters.filter { it.selected },
                greetingState = GroupChatCreateGreetingState(
                    characters = previewCharacters.filter { character -> character.selected }
                        .map { character ->
                            GroupChatGreetingCharacterItem(
                                id = character.id,
                                name = character.name,
                                greetings = character.greetings,
                            )
                        },
                    selectedCharacterId = 1,
                    selectedGreetingIndex = 0,
                ),
            ),
        )
    }
}

private val previewCharacters = listOf(
    GroupChatCreateCharacterItem(
        id = 1,
        name = "Lyra",
        description = "A curious star navigator.",
        selected = true,
        greetings = listOf("Lyra studies the unfamiliar stars.", "We made it."),
    ),
    GroupChatCreateCharacterItem(
        id = 2,
        name = "Mina",
        description = "A calm archivist with a sharp memory.",
        selected = true,
        greetings = listOf("Mina opens the archive."),
    ),
    GroupChatCreateCharacterItem(
        id = 3,
        name = "Rowan",
        description = "An impulsive but loyal explorer.",
        selected = false,
    ),
)
