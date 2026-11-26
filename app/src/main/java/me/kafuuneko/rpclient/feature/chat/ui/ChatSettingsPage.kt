package me.kafuuneko.rpclient.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.feature.agent.ui.AgentSettingsDialogHost
import me.kafuuneko.rpclient.feature.agent.ui.AgentSettingsPanel
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatSettingsState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
import me.kafuuneko.rpclient.ui.widgets.PageButton as Button
import me.kafuuneko.rpclient.ui.widgets.PageSwitch as Switch
import me.kafuuneko.rpclient.ui.widgets.RpLazyColumn
import me.kafuuneko.rpclient.ui.widgets.RpScrollableOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.SessionWorldBookEntryItem
import me.kafuuneko.rpclient.ui.widgets.SessionWorldBookGroupItem
import me.kafuuneko.rpclient.ui.widgets.SessionWorldBookSettingsSection
import me.kafuuneko.rpclient.ui.widgets.SettingsActionRow
import me.kafuuneko.rpclient.ui.widgets.SettingsSection
import me.kafuuneko.rpclient.ui.widgets.UserPersonaPicker
import me.kafuuneko.rpclient.ui.widgets.pageCanvasColor

/**
 * 渲染单聊设置草稿，文本、选择和开关统一由保存按钮提交。
 *
 * @param state 当前设置草稿
 * @param lorebookState 会话世界书状态
 * @param loadState 当前加载状态
 * @param showCharacterExamplesSwitch 是否展示角色卡示例对话开关
 * @param emit 原有单聊意图发送入口
 */
@Composable
internal fun ChatSettingsPage(
    state: ChatSettingsState,
    lorebookState: ChatLorebookState,
    loadState: ChatLoadState,
    showCharacterExamplesSwitch: Boolean,
    emit: ChatUiIntent.() -> Unit,
    agentState: AgentUiState = AgentUiState(),
) {
    // 草稿由 ViewModel 持有，列表项移出视口或页面恢复都不会丢失输入。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = stringResource(R.string.chat_settings),
            onBack = { ChatUiIntent.CloseChatSettings.emit() },
            actions = {
                TextButton(onClick = { ChatUiIntent.SaveSettings.emit() }) {
                    Text(stringResource(R.string.save))
                }
            },
        )
        RpLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSection(title = stringResource(R.string.chat_settings_actions)) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Psychology,
                        title = stringResource(R.string.memory_editor),
                        onClick = { ChatUiIntent.OpenMemory.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.regenerate_latest_reply),
                        onClick = { ChatUiIntent.RegenerateLast.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(R.string.continue_latest_reply),
                        onClick = { ChatUiIntent.ContinueLast.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Edit,
                        title = stringResource(R.string.impersonate_user),
                        onClick = { ChatUiIntent.ImpersonateUser.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.FileUpload,
                        title = stringResource(R.string.export_chat),
                        enabled = loadState == ChatLoadState.None,
                        onClick = { ChatUiIntent.ExportChatClick.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Delete,
                        title = stringResource(R.string.delete_chat_title),
                        iconTint = MaterialTheme.colorScheme.error,
                        enabled = loadState == ChatLoadState.None,
                        onClick = { ChatUiIntent.DeleteSessionClick.emit() },
                    )
                }
            }
            item { AgentSettingsPanel(agentState) { ChatUiIntent.AgentAction(it).emit() } }
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    if (showCharacterExamplesSwitch) {
                        SessionSwitchRow(
                            title = stringResource(R.string.chat_include_character_examples),
                            checked = state.includeCharacterExamples,
                            enabled = loadState == ChatLoadState.None,
                            onCheckedChange = { enabled ->
                                ChatUiIntent.ToggleIncludeCharacterExamples(enabled).emit()
                            },
                        )
                    }
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.title)) },
                        value = state.titleDraft,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onValueChange = { ChatUiIntent.ChangeTitle(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.current_summary)) },
                        value = state.summaryDraft,
                        placeholder = { Text(stringResource(R.string.no_summary_yet)) },
                        minLines = 3,
                        maxLines = 8,
                        onValueChange = { ChatUiIntent.ChangeSummary(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                    // 摘要操作紧随正文，与群聊保持一致，便于查看内容后直接处理。
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(
                            if (state.canResummarize) R.string.resummarize else R.string.summarize_now
                        ),
                        onClick = { ChatUiIntent.SummarizeNow.emit() },
                    )
                    SettingsActionRow(
                        icon = Icons.Rounded.Refresh,
                        title = stringResource(R.string.restore_previous_summary),
                        onClick = { ChatUiIntent.RestorePreviousSummary.emit() },
                    )
                    SessionSwitchRow(
                        title = stringResource(R.string.pause_auto_summary),
                        checked = state.autoSummaryPaused,
                        onCheckedChange = { enabled ->
                            ChatUiIntent.ToggleAutoSummaryPaused(enabled).emit()
                        },
                    )
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.user_note)) },
                        value = state.userNoteDraft,
                        placeholder = { Text(stringResource(R.string.empty)) },
                        minLines = 3,
                        maxLines = 8,
                        onValueChange = { ChatUiIntent.ChangeUserNote(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                    UserPersonaPicker(
                        state = state.persona,
                        onSelect = { ChatUiIntent.SelectPersona(it).emit() })
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(state.persona.selectedName.orEmpty()) },
                        alwaysMinimizeLabel = true,
                        label = { Text(stringResource(R.string.user_display_name)) },
                        value = state.userNameDraft,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onValueChange = { ChatUiIntent.ChangeUserName(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.user_persona_description)) },
                        value = state.userDescriptionDraft,
                        placeholder = { Text(state.inheritedDescription.ifEmpty { stringResource(R.string.user_persona_description) }) },
                        alwaysMinimizeLabel = true,
                        minLines = 3,
                        maxLines = 8,
                        onValueChange = { ChatUiIntent.ChangeUserDescription(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                    RpScrollableOutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.creator_notes)) },
                        value = state.creatorNotesDraft,
                        placeholder = {
                            Text(state.inheritedCreatorNotes.ifEmpty {
                                stringResource(R.string.using_character_default_or_empty)
                            })
                        },
                        alwaysMinimizeLabel = true,
                        minLines = 3,
                        maxLines = 8,
                        onValueChange = { ChatUiIntent.ChangeCreatorNotes(it).emit() },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
            item {
                val enabledWorldBooks = remember(lorebookState.enabledGroups) {
                    lorebookState.enabledGroups.map(ChatLorebookGroupItem::toSessionWorldBookGroupItem)
                }
                SessionWorldBookSettingsSection(
                    totalGroupCount = lorebookState.groups.size,
                    enabledGroups = enabledWorldBooks,
                    onOpenWorldBookManager = { ChatUiIntent.OpenWorldBookManager.emit() },
                    onAddWorldBooks = { ChatUiIntent.ShowAddLorebooksDialog.emit() },
                    onSearchWorldBooks = { ChatUiIntent.ShowSessionLoreDialog.emit() },
                    onToggleLorebook = { lorebookId ->
                        ChatUiIntent.ToggleSessionLorebook(lorebookId).emit()
                    },
                    onToggleEntry = { entryId ->
                        ChatUiIntent.ToggleSessionLoreEntry(entryId).emit()
                    },
                )
            }
            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { ChatUiIntent.SaveSettings.emit() },
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
    AgentSettingsDialogHost(
        state = agentState,
        emit = { action -> ChatUiIntent.AgentAction(action).emit() },
    )
}

/** 将单聊世界书分组转换为设置页通用世界书展示模型。 */
private fun ChatLorebookGroupItem.toSessionWorldBookGroupItem(): SessionWorldBookGroupItem {
    // 保留未启用条目，设置页展开后仍可重新开启同一本世界书内的其他条目。
    return SessionWorldBookGroupItem(
        lorebookId = lorebookId,
        lorebookName = lorebookName,
        enabledCount = enabledCount,
        totalCount = totalCount,
        entries = entries.map { entry ->
            SessionWorldBookEntryItem(
                id = entry.id,
                name = entry.name,
                content = entry.content,
                keywords = entry.keywords,
                constant = entry.constant,
                enabled = entry.enabled,
            )
        },
    )
}

/** 会话分区中的开关共用文字起点与右侧控件位置，不再叠加设置行内边距。 */
@Composable
private fun SessionSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    // 分区容器已经提供外边距，行内只按剩余宽度分配文字和开关。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
