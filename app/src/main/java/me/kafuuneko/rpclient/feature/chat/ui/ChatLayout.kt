package me.kafuuneko.rpclient.feature.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.appearance.presentation.PageBackgroundUiState
import me.kafuuneko.rpclient.feature.chat.model.ChatCharacterItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookEntryItem
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatMessageUiModel
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.model.MessageRole
import me.kafuuneko.rpclient.feature.chat.presentation.ChatConversationState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatDialogState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatPage
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiState
import me.kafuuneko.rpclient.libs.media.MessageImageAction
import me.kafuuneko.rpclient.model.MessageContentPart
import me.kafuuneko.rpclient.ui.dialog.AppConfirmDialog
import me.kafuuneko.rpclient.ui.dialog.AppDangerDialog
import me.kafuuneko.rpclient.ui.dialog.LoadingDialog
import me.kafuuneko.rpclient.ui.dialog.PromptInspectorDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialog
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogEntry
import me.kafuuneko.rpclient.ui.dialog.SessionLorebookDialogGroup
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.theme.DefaultCharacterAccentColor
import me.kafuuneko.rpclient.ui.widgets.PageBackdrop
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor

/** 单角色聊天页 Compose 入口，根据页面状态切换会话区与设置区。 */
@Composable
fun ChatLayout(
    uiState: ChatUiState,
    background: PageBackgroundUiState = PageBackgroundUiState(),
    emit: ChatUiIntent.() -> Unit,
) {
    BackHandler(enabled = uiState is ChatUiState.Normal, onBack = { ChatUiIntent.Back.emit() })
    when (uiState) {
        ChatUiState.None -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageCanvasColor()),
            )
        }

        is ChatUiState.Finished -> ChatLayout(uiState = uiState.previous, background = background, emit = {})

        is ChatUiState.Normal -> {
            // 页面切换只卸载视图，保留会话和设置各自的列表位置。
            val pageStateHolder = rememberSaveableStateHolder()
            // 设置子页沿用会话背景，公共表面按该页内容区参数装饰。
            PageBackdrop(state = background, useContainerAppearance = uiState.page == ChatPage.Settings) {
                pageStateHolder.SaveableStateProvider("${uiState.session.id}:${uiState.page.name}") {
                    when (uiState.page) {
                        ChatPage.Conversation -> ChatNormalPage(uiState, emit)
                        ChatPage.Settings -> ChatSettingsPage(
                            session = uiState.session,
                            lorebookState = uiState.lorebookState,
                            loadState = uiState.loadState,
                            emit = emit,
                        )
                    }
                }
            }
            DialogSwitch(uiState, emit)
        }
    }
}

@Composable
private fun DialogSwitch(
    state: ChatUiState.Normal,
    emit: ChatUiIntent.() -> Unit,
) {
    when (val dialogState = state.dialogState) {
        ChatDialogState.None -> Unit

        is ChatDialogState.SessionLorebook -> ChatSessionLorebookDialog(
            groups = state.lorebookState.groups,
            dialogState = dialogState,
            emit = emit,
        )

        ChatDialogState.SummaryTokenLimit -> AppConfirmDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.summary_token_limit_title),
            message = stringResource(
                R.string.summary_token_limit_message,
                stringResource(R.string.summary_memory),
                stringResource(R.string.general_summary_memory),
                stringResource(R.string.summary_response_tokens),
            ),
            confirmText = stringResource(R.string.summary_go_to_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.OpenSummarySettings.emit() },
        )

        is ChatDialogState.ModelSettingsGuide -> AppConfirmDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = dialogState.title,
            message = dialogState.message,
            confirmText = stringResource(R.string.go_to_model_settings),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.OpenProviderSettings.emit() },
        )

        ChatDialogState.Exporting -> LoadingDialog(
            title = stringResource(R.string.exporting_chat),
            description = stringResource(R.string.export_chat_desc),
        )

        ChatDialogState.Summarizing -> LoadingDialog(
            title = stringResource(R.string.updating_summary),
            description = stringResource(R.string.summarize_now_desc),
            onCancel = { ChatUiIntent.CancelSummary.emit() },
        )

        is ChatDialogState.PromptInspector -> PromptInspectorDialog(
            inspection = dialogState.inspection,
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            onCopyRequest = { ChatUiIntent.CopyPromptItem(it).emit() },
            onPreviewImages = { ids, index ->
                ChatUiIntent.ImageAction(MessageImageAction.Preview(ids, index))
                    .emit()
            },
        )

        is ChatDialogState.DeleteSessionConfirm -> AppDangerDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_chat_title),
            message = stringResource(R.string.delete_chat_message, dialogState.sessionTitle),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.ConfirmDeleteSession.emit() },
        )

        is ChatDialogState.DeleteMessageConfirm -> AppDangerDialog(
            onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
            title = stringResource(R.string.delete_message_title),
            message = stringResource(R.string.delete_message_confirm),
            confirmText = stringResource(R.string.delete),
            dismissText = stringResource(R.string.cancel),
            onConfirm = { ChatUiIntent.ConfirmDeleteMessage(dialogState.messageId).emit() },
        )
    }
}

/** 将单聊世界书状态适配到应用级快捷管理对话框。 */
@Composable
private fun ChatSessionLorebookDialog(
    groups: List<ChatLorebookGroupItem>,
    dialogState: ChatDialogState.SessionLorebook,
    emit: ChatUiIntent.() -> Unit,
) {
    // 映射只包含通用 Dialog 所需的展示字段，确认后的持久化仍由单聊状态层负责。
    val dialogGroups = remember(groups) {
        groups.map(ChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    val visibleDialogGroups = remember(dialogState.visibleGroups) {
        dialogState.visibleGroups.map(ChatLorebookGroupItem::toSessionLorebookDialogGroup)
    }
    SessionLorebookDialog(
        groups = dialogGroups,
        visibleGroups = visibleDialogGroups,
        query = dialogState.query,
        enabledEntryIds = dialogState.enabledEntryIds,
        onQueryChange = { ChatUiIntent.ChangeSessionLorebookDialogQuery(it).emit() },
        onToggleGroup = { ChatUiIntent.ToggleSessionLorebookDialogGroup(it).emit() },
        onToggleEntry = { ChatUiIntent.ToggleSessionLorebookDialogEntry(it).emit() },
        onConfirmSelection = { ChatUiIntent.ConfirmSessionLorebookSelection.emit() },
        onManageWorldBooks = { ChatUiIntent.OpenWorldBookManager.emit() },
        onDismissRequest = { ChatUiIntent.DismissDialog.emit() },
    )
}

/** 转换单聊世界书分组为通用对话框展示模型。 */
private fun ChatLorebookGroupItem.toSessionLorebookDialogGroup(): SessionLorebookDialogGroup {
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

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun ChatLayoutPreview() {
    AppTheme(dynamicColor = false) {
        ChatLayout(
            uiState = ChatUiState.Normal(
                session = ChatSessionItem(
                    id = 1,
                    title = "The Seventh File on a Rainy Night",
                    summarize = "",
                    userNote = "",
                    userName = "You",
                    userDescription = "",
                    creatorNotes = "",
                    autoSummaryPaused = false,
                    messageCount = 1,
                    enabledLorebookEntryIds = setOf(1),
                ),
                character = ChatCharacterItem(
                    id = 1,
                    name = "Lyra",
                    description = "",
                    personality = "",
                    scenario = "",
                    examplesOfDialogue = "",
                    postHistoryInstructions = "",
                    creatorNotes = "",
                    avatarText = "L",
                    accentColor = DefaultCharacterAccentColor,
                ),
                conversationState = ChatConversationState(
                    messages = listOf(
                        element = ChatMessageUiModel(
                            id = "1",
                            role = MessageRole.Assistant,
                            speaker = "Lyra",
                            content = "## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf",
                            parts = listOf(element = MessageContentPart.Text(content = "## Archive note\nThe rain kept **tapping** on the archive windows.\n\n- Index the file\n- Check `sealed` shelf")),
                            time = "02:15",
                            tokenCount = 12,
                        ),
                    ),
                    hasAssistantMessage = true,
                ),
                lorebookState = ChatLorebookState(
                    groups = listOf(
                        element = ChatLorebookGroupItem(
                            lorebookId = 1,
                            lorebookName = "Fog Harbor",
                            enabledCount = 1,
                            totalCount = 1,
                            entries = listOf(
                                element = ChatLorebookEntryItem(
                                    id = 1,
                                    lorebookId = 1,
                                    lorebookName = "Fog Harbor",
                                    name = "Old District",
                                    keywords = listOf("rain"),
                                    secondaryKeywords = emptyList(),
                                    constant = false,
                                    order = 0,
                                    depth = 0,
                                    content = "",
                                    enabled = true,
                                ),
                            ),
                        ),
                    ),
                ),
                streamEnabled = true,
                dialogState = ChatDialogState.SessionLorebook(
                    query = "",
                    visibleGroups = emptyList(),
                    enabledEntryIds = setOf(1),
                ),
            ),
            emit = {},
        )
    }
}
