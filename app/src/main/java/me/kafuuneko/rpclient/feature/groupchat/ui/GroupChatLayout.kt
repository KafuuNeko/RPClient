package me.kafuuneko.rpclient.feature.groupchat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMemberItem
import me.kafuuneko.rpclient.feature.groupchat.model.GroupChatMessageItem
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatConversationState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatDialogState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatLoadState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatPage
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatSettingsState
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiIntent
import me.kafuuneko.rpclient.feature.groupchat.presentation.GroupChatUiState
import me.kafuuneko.rpclient.libs.core.ActivityPreview
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatActivationStrategy
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatLorebookGroupItem
import me.kafuuneko.rpclient.libs.groupchat.model.GroupChatMessageSource
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop

/** 群聊页 Compose 入口，根据状态渲染对话、成员与世界书设置。 */
@Composable
fun GroupChatLayout(
    uiState: GroupChatUiState,
    background: PageBackgroundUiState = PageBackgroundUiState(),
    emitIntent: (GroupChatUiIntent) -> Unit = {},
) {
    BackHandler(
        enabled = uiState is GroupChatUiState.Normal,
        onBack = {
            emitIntent(GroupChatUiIntent.Back)
        },
    )
    when (uiState) {
        GroupChatUiState.None -> Unit
        is GroupChatUiState.Finished -> GroupChatLayout(uiState.previous, background) {}
        is GroupChatUiState.Normal -> {
            // 页面切换只卸载视图，保留会话和设置各自的列表位置。
            val pageStateHolder = rememberSaveableStateHolder()
            // 群聊设置与会话使用相同背景，不能落回 Activity 的通用背景。
            PageBackdrop(
                state = background,
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
            DialogSwitch(uiState, emitIntent)
            LoadStateOverlay(uiState.loadState)
        }
    }
}

@Composable
private fun DialogSwitch(
    state: GroupChatUiState.Normal,
    emitIntent: (GroupChatUiIntent) -> Unit,
) {
    when (val dialogState = state.dialogState) {
        GroupChatDialogState.None -> Unit
        is GroupChatDialogState.SessionLorebook -> GroupSessionLorebookDialog(
            groups = state.settingsState.lorebookGroups,
            dialogState = dialogState,
            emitIntent = emitIntent,
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

        is GroupChatDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { emitIntent(GroupChatUiIntent.DismissDialog) },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { emitIntent(GroupChatUiIntent.OpenProviderSettings) },
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
        onQueryChange = { emitIntent(GroupChatUiIntent.ChangeSessionLorebookDialogQuery(it)) },
        onToggleGroup = { emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogGroup(it)) },
        onToggleEntry = { emitIntent(GroupChatUiIntent.ToggleSessionLorebookDialogEntry(it)) },
        onConfirmSelection = {
            emitIntent(GroupChatUiIntent.ConfirmSessionLorebookSelection)
        },
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

@Composable
private fun LoadStateOverlay(loadState: GroupChatLoadState) {
    if (loadState == GroupChatLoadState.None) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        AppSurface(
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                val loadingText = when (loadState) {
                    GroupChatLoadState.Deleting -> stringResource(R.string.deleting)
                    GroupChatLoadState.Saving -> stringResource(R.string.saving)
                    GroupChatLoadState.Summarizing ->
                        stringResource(R.string.updating_summary)

                    else -> stringResource(R.string.loading)
                }
                Text(text = loadingText)
            }
        }
    }
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
