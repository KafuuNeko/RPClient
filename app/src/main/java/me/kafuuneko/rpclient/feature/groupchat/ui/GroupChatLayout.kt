package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatConversationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.appearance.RenderedPageBackground
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.ui.dialog.AddSessionLorebooksDialog
import me.kafuuneko.rpclient.ui.dialog.AppActionItem
import me.kafuuneko.rpclient.ui.dialog.AppActionListDialog
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialog
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerDialogItem
import me.kafuuneko.rpclient.ui.dialog.CharacterPickerMode
import me.kafuuneko.rpclient.ui.dialog.DialogBadgeTone
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppLoadingPage
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop

/**
 * 群聊页 Compose 入口，根据状态渲染加载、对话和设置页面。
 *
 * @param uiState 当前页面状态。
 * @param background 当前群聊的页面背景。
 * @param emitIntent 页面交互意图的接收入口。
 */
@Composable
fun GroupChatLayout(
    uiState: GroupChatUiState,
    background: RenderedPageBackground? = null,
    emitIntent: (GroupChatUiIntent) -> Unit = {},
) {
    BackHandler(
        enabled = uiState is GroupChatUiState.Loading || uiState is GroupChatUiState.Normal,
        onBack = {
            emitIntent(GroupChatUiIntent.Back)
        },
    )
    when (uiState) {
        GroupChatUiState.None, GroupChatUiState.Loading -> AppLoadingPage(
            title = stringResource(R.string.group_chat),
            background = background,
            onBack = { emitIntent(GroupChatUiIntent.Back) },
        )

        is GroupChatUiState.Finished -> GroupChatLayout(
            uiState = uiState.previous,
            background = background,
            emitIntent = {},
        )

        is GroupChatUiState.Normal -> {
            // 页面切换只卸载视图，保留会话和设置各自的列表位置。
            val pageStateHolder = rememberSaveableStateHolder()
            // 群聊设置与会话使用相同背景，不能落回 Activity 的通用背景。
            PageBackdrop(
                background = background,
                useContainerAppearance = uiState.page == GroupChatPage.Settings,
            ) {
                pageStateHolder.SaveableStateProvider("${uiState.sessionId}:${uiState.page.name}") {
                    if (uiState.page == GroupChatPage.Settings) {
                        GroupChatSettingsPage(
                            state = uiState.settingsState,
                            members = uiState.members,
                            emitIntent = emitIntent,
                        )
                    } else {
                        GroupChatNormalPage(uiState, emitIntent)
                    }
                }
            }
            DialogSwitch(state = uiState, emitIntent = emitIntent)
        }
    }
}

@Composable
private fun DialogSwitch(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 会话就绪后的弹窗由互斥的业务对话框状态统一分发。
    when (val dialogState = state.dialogState) {
        GroupChatDialogState.None -> Unit
        is GroupChatDialogState.SessionLorebook -> GroupSessionLorebookDialog(
            groups = state.settingsState.lorebookGroups,
            dialogState = dialogState,
            emitIntent = emitIntent,
        )

        is GroupChatDialogState.AddMembers -> GroupAddMembersDialog(
            totalCandidateCount = state.settingsState.addableCharacterCount,
            dialogState = dialogState,
            emitIntent = emitIntent,
        )

        is GroupChatDialogState.AddLorebooks -> GroupAddLorebooksDialog(
            dialogState = dialogState,
            emitIntent = emitIntent,
        )

        GroupChatDialogState.Saving -> LoadingDialog(
            title = stringResource(R.string.saving),
            description = stringResource(R.string.group_chat_settings),
        )

        GroupChatDialogState.UnsavedSettingsChanges -> AppActionListDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.group_chat_save_settings_title),
            subtitle = stringResource(R.string.group_chat_save_settings_message),
            badgeIcon = Icons.Rounded.Save,
            dismissText = stringResource(R.string.cancel),
            actions = listOf(
                AppActionItem(
                    icon = Icons.Rounded.Save,
                    title = stringResource(R.string.save),
                    onClick = { emitIntent(GroupChatUiIntent.SaveSettings) },
                ),
                AppActionItem(
                    icon = Icons.Rounded.DeleteOutline,
                    title = stringResource(R.string.discard_changes),
                    isDestructive = true,
                    onClick = { emitIntent(GroupChatUiIntent.DiscardSettingsChanges) },
                ),
            ),
        )

        GroupChatDialogState.Branching -> LoadingDialog(
            title = stringResource(R.string.saving),
            description = stringResource(R.string.branch_from_message),
        )

        GroupChatDialogState.Deleting -> LoadingDialog(
            title = stringResource(R.string.deleting),
            description = state.title,
        )

        GroupChatDialogState.Summarizing -> LoadingDialog(
            title = stringResource(R.string.updating_summary),
            description = stringResource(R.string.summarize_now_desc),
            onCancel = { emitIntent(GroupChatUiIntent.CancelSummary) },
        )

        GroupChatDialogState.SummaryTokenLimit -> AppConfirmDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.summary_token_limit_title),
            message = stringResource(
                R.string.summary_token_limit_message,
                stringResource(R.string.summary_memory),
                stringResource(R.string.general_summary_memory),
                stringResource(R.string.summary_response_tokens),
            ),
            confirmText = stringResource(R.string.summary_go_to_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.OpenSummarySettings) },
        )

        is GroupChatDialogState.GenerationFailure -> AppConfirmDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = dialogState.title,
            message = dialogState.message,
            badgeIcon = Icons.Rounded.ErrorOutline,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(if (dialogState.canRetry) R.string.retry else R.string.close),
            dismissText = if (dialogState.canRetry) stringResource(R.string.cancel) else null,
            onConfirm = {
                if (dialogState.canRetry) {
                    emitIntent(GroupChatUiIntent.RetryGeneration)
                } else {
                    emitIntent(GroupChatUiIntent.DismissDialog)
                }
            },
            properties = DialogProperties(dismissOnClickOutside = false),
        )

        is GroupChatDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = dialogState.title,
            message = dialogState.message,
            badgeIcon = Icons.Rounded.ErrorOutline,
            badgeTone = DialogBadgeTone.Danger,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.OpenProviderSettings) },
            properties = DialogProperties(dismissOnClickOutside = false),
        )

        is GroupChatDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            onCopyRequest = { emitIntent(GroupChatUiIntent.CopyPromptItem(it)) },
            onPreviewImages = { ids, index ->
                emitIntent(GroupChatUiIntent.ImageAction(MessageImageAction.Preview(ids, index)))
            },
        )

        is GroupChatDialogState.DeleteMessageConfirm -> AppDangerDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.delete_message_title),
            message = stringResource(R.string.delete_message_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.ConfirmDeleteMessage) },
        )

        is GroupChatDialogState.DeleteSessionConfirm -> AppDangerDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = stringResource(R.string.group_chat_delete_title),
            message = stringResource(
                R.string.group_chat_delete_message,
                dialogState.title,
            ),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.ConfirmDeleteSession) },
        )
    }
}

/** 将群聊世界书状态适配到应用级快捷管理对话框。 */
@Composable
private fun GroupSessionLorebookDialog(
    groups: List<GroupChatLorebookGroupItem>,
    dialogState: GroupChatDialogState.SessionLorebook,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 通用 Dialog 只接收展示字段，确认后的会话级持久化仍由群聊状态层负责。
    val dialogGroups = remember(groups) {
        groups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    SessionLorebookDialog(
        groups = dialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        enabledEntryIds = dialogState.enabledEntryIds,
        showSelectedOnly = dialogState.showSelectedOnly,
        onQueryChange = { emitIntent(GroupChatUiIntent.ChangeSessionLorebookDialogQuery(it)) },
        onShowSelectedOnlyChange = { selectedOnly ->
            emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogSelectedOnly(selectedOnly))
        },
        onToggleGroup = { emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogGroup(it)) },
        onToggleEntry = { emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogEntry(it)) },
        onConfirmSelection = {
            emitIntent(GroupChatUiIntent.ConfirmSessionLorebookSelection)
        },
        onManageWorldBooks = { emitIntent(GroupChatUiIntent.OpenWorldBookManager) },
        onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
    )
}

/** 将群聊设置页的添加成员弹窗状态适配到通用角色多选检索弹窗。 */
@Composable
private fun GroupAddMembersDialog(
    totalCandidateCount: Int,
    dialogState: GroupChatDialogState.AddMembers,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 将候选角色映射为通用选择器模型，未勾选任何角色时禁用提交按钮
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
    val selectedCount = dialogState.selectedCharacterIds.size
    val confirmText = if (selectedCount > 0) {
        stringResource(R.string.picker_add_confirm, selectedCount)
    } else {
        stringResource(R.string.confirm)
    }
    CharacterPickerDialog(
        title = stringResource(R.string.group_chat_add_members_title),
        mode = CharacterPickerMode.Multiple,
        totalCount = totalCandidateCount,
        visibleCharacters = visibleItems,
        characterAvatars = dialogState.characterAvatars,
        selectedIds = dialogState.selectedCharacterIds,
        query = dialogState.query,
        showSelectedOnly = dialogState.showSelectedOnly,
        confirmText = confirmText,
        confirmEnabled = selectedCount > 0,
        emptyText = stringResource(R.string.group_chat_all_characters_added),
        onQueryChange = { text -> emitIntent(GroupChatUiIntent.ChangeAddMembersQuery(text)) },
        onShowSelectedOnlyChange = { selectedOnly ->
            emitIntent(GroupChatUiIntent.ToggleAddMembersSelectedOnly(selectedOnly))
        },
        onVisibleCharactersChanged = { visibleIds, targetSizePx ->
            emitIntent(
                GroupChatUiIntent.VisibleAddMemberCharactersChanged(
                    characterIds = visibleIds,
                    targetSizePx = targetSizePx,
                ),
            )
        },
        onCharacterClick = { id -> emitIntent(GroupChatUiIntent.ToggleAddMemberSelection(id)) },
        onConfirm = { emitIntent(GroupChatUiIntent.ConfirmAddMembers) },
        onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
    )
}

/** 将群聊设置页的添加新世界书弹窗状态适配到通用添加世界书弹窗。 */
@Composable
private fun GroupAddLorebooksDialog(
    dialogState: GroupChatDialogState.AddLorebooks,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    // 仅向选择器传入当前未启用的候选世界书，确认后与既有已启用条目合并持久化
    val dialogGroups = remember(dialogState.candidateGroups) {
        dialogState.candidateGroups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(GroupChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    AddSessionLorebooksDialog(
        groups = dialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        selectedEntryIds = dialogState.selectedEntryIds,
        showSelectedOnly = dialogState.showSelectedOnly,
        onQueryChange = { text -> emitIntent(GroupChatUiIntent.ChangeAddLorebooksQuery(text)) },
        onShowSelectedOnlyChange = { selectedOnly ->
            emitIntent(GroupChatUiIntent.ToggleAddLorebooksSelectedOnly(selectedOnly))
        },
        onToggleGroup = { id -> emitIntent(GroupChatUiIntent.ToggleAddLorebooksGroup(id)) },
        onToggleEntry = { id -> emitIntent(GroupChatUiIntent.ToggleAddLorebooksEntry(id)) },
        onConfirmSelection = { emitIntent(GroupChatUiIntent.ConfirmAddLorebooks) },
        onManageWorldBooks = { emitIntent(GroupChatUiIntent.OpenWorldBookManager) },
        onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
    )
}

/** 转换群聊世界书分组为通用对话框展示模型。 */
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

/**
 * 提供群聊主页和设置页共用的发言策略名称。
 *
 * @return 当前发言策略的字符串资源 ID
 */
internal fun GroupChatActivationStrategy.titleRes(): Int {
    return when (this) {
        GroupChatActivationStrategy.Manual -> R.string.group_chat_strategy_manual
        GroupChatActivationStrategy.Natural -> R.string.group_chat_strategy_natural
        GroupChatActivationStrategy.List -> R.string.group_chat_strategy_list
        GroupChatActivationStrategy.Pooled -> R.string.group_chat_strategy_pooled
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun GroupChatLoadingPreview() {
    AppTheme(darkTheme = false, dynamicColor = false) {
        GroupChatLayout(uiState = GroupChatUiState.Loading)
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun GroupChatPreview() {
    ActivityPreview(darkTheme = false) {
        GroupChatLayout(
            uiState = GroupChatUiState.Normal(
                sessionId = 1,
                title = "Starlight Crew",
                members = previewMembers,
                activeActivationStrategy = GroupChatActivationStrategy.Natural,
                conversationState = GroupChatConversationState(
                    messages = previewMessages,
                    hasCharacterMessage = true,
                    selectedSpeakerId = 1,
                ),
                settingsState = GroupChatSettingsState(activationStrategy = GroupChatActivationStrategy.Natural),
            ),
        )
    }
}

private val previewMembers = listOf(
    GroupChatMemberItem(id = 1, name = "Lyra", description = "", muted = false),
    GroupChatMemberItem(id = 2, name = "Mina", description = "", muted = false),
    GroupChatMemberItem(id = 3, name = "Rowan", description = "", muted = true),
)

private val previewMessages = listOf(
    GroupChatMessageItem(
        id = 1,
        source = GroupChatMessageSource.User,
        speakerName = "You",
        content = "The signal is coming from the abandoned station.",
        time = "21:04",
    ),
    GroupChatMessageItem(
        id = 2,
        source = GroupChatMessageSource.Character,
        speakerName = "Lyra",
        content = "Then we should approach quietly. Its navigation lights are still active.",
        time = "21:04",
    ),
    GroupChatMessageItem(
        id = 3,
        source = GroupChatMessageSource.Character,
        speakerName = "Mina",
        content = "I will check the archive for its last registered crew.",
        time = "21:05",
    ),
)
