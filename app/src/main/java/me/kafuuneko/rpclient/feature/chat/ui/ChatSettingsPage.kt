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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.chat.model.ChatLorebookGroupItem
import me.kafuuneko.rpclient.feature.chat.model.ChatSessionItem
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLoadState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatLorebookState
import me.kafuuneko.rpclient.feature.chat.presentation.ChatUiIntent
import me.kafuuneko.rpclient.ui.widgets.AppTopBar
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
 * 渲染单聊设置页，字段草稿与自动保存时序沿用原实现。
 *
 * @param session 当前会话配置
 * @param lorebookState 会话世界书状态
 * @param loadState 当前加载状态
 * @param showCharacterExamplesSwitch 是否展示角色卡示例对话开关
 * @param emit 原有单聊意图发送入口
 */
@Composable
internal fun ChatSettingsPage(
    session: ChatSessionItem,
    lorebookState: ChatLorebookState,
    loadState: ChatLoadState,
    showCharacterExamplesSwitch: Boolean,
    emit: ChatUiIntent.() -> Unit,
) {
    // 沿用会话和世界书状态节点，各设置项继续发送原有意图。
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageCanvasColor()),
    ) {
        AppTopBar(
            title = stringResource(R.string.chat_settings),
            onBack = { ChatUiIntent.CloseChatSettings.emit() },
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
            item {
                SettingsSection(title = stringResource(R.string.session)) {
                    if (showCharacterExamplesSwitch) {
                        SessionSwitchRow(
                            title = stringResource(R.string.chat_include_character_examples),
                            checked = session.includeCharacterExamples,
                            enabled = loadState == ChatLoadState.None,
                            onCheckedChange = { enabled ->
                                ChatUiIntent.ToggleIncludeCharacterExamples(enabled).emit()
                            },
                        )
                    }
                    AutoSaveTextField(
                        label = stringResource(R.string.title),
                        value = session.title,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { text -> ChatUiIntent.SaveTitle(text).emit() },
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.current_summary),
                        value = session.summarize,
                        placeholder = stringResource(R.string.no_summary_yet),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { text -> ChatUiIntent.SaveSummary(text).emit() },
                    )
                    // 摘要操作紧随正文，与群聊保持一致，便于查看内容后直接处理。
                    SettingsActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = stringResource(
                            if (session.canResummarize) R.string.resummarize else R.string.summarize_now
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
                        checked = session.autoSummaryPaused,
                        onCheckedChange = { enabled ->
                            ChatUiIntent.ToggleAutoSummaryPaused(enabled).emit()
                        },
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_note),
                        value = session.userNote,
                        placeholder = stringResource(R.string.empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { text -> ChatUiIntent.SaveUserNote(text).emit() },
                    )
                    UserPersonaPicker(
                        state = session.persona,
                        onSelect = { ChatUiIntent.SelectPersona(it).emit() })
                    AutoSaveTextField(
                        placeholder = session.persona.selectedName.orEmpty(),
                        alwaysMinimizeLabel = true,
                        label = stringResource(R.string.user_display_name),
                        value = session.userName,
                        minLines = 1,
                        maxLines = 1,
                        singleLine = true,
                        onSave = { text -> ChatUiIntent.SaveUserName(text).emit() },
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.user_persona_description),
                        value = session.userDescription,
                        placeholder = session.inheritedDescription.ifEmpty { stringResource(R.string.user_persona_description) },
                        alwaysMinimizeLabel = true,
                        minLines = 3,
                        maxLines = 8,
                        onSave = { text -> ChatUiIntent.SaveUserDescription(text).emit() },
                    )
                    AutoSaveTextField(
                        label = stringResource(R.string.creator_notes),
                        value = session.creatorNotes,
                        placeholder = stringResource(R.string.using_character_default_or_empty),
                        minLines = 3,
                        maxLines = 8,
                        onSave = { text -> ChatUiIntent.SaveCreatorNotes(text).emit() },
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
                    onToggleLorebook = { lorebookId ->
                        ChatUiIntent.ToggleSessionLorebook(lorebookId).emit()
                    },
                    onToggleEntry = { entryId ->
                        ChatUiIntent.ToggleSessionLoreEntry(entryId).emit()
                    },
                )
            }
        }
    }
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

/** 自动保存用户编辑，外部加载的正文直接同步到输入框。 */
@Composable
private fun AutoSaveTextField(
    label: String,
    value: String,
    placeholder: String? = null,
    alwaysMinimizeLabel: Boolean = false,
    minLines: Int,
    maxLines: Int,
    singleLine: Boolean = false,
    onSave: (String) -> Unit,
) {
    var draft by remember(label) { mutableStateOf(value) }
    var pendingEdit by remember(label) { mutableStateOf<String?>(null) }
    val submittedEdits = remember(label) { ArrayDeque<String>() }
    val savedValue by rememberUpdatedState(value)
    val save by rememberUpdatedState(onSave)

    LaunchedEffect(value) {
        // 较早一次保存的回显不能替换用户随后输入的草稿。
        val acknowledged = submittedEdits.indexOf(value)
        if (acknowledged >= 0) {
            repeat(acknowledged + 1) { submittedEdits.removeFirst() }
        } else {
            // 生成或恢复的摘要即使在输入框聚焦时也应更新正文。
            submittedEdits.clear()
            pendingEdit = null
            draft = value
        }
    }

    LaunchedEffect(pendingEdit) {
        // 只有输入回调提交的修改能够触发防抖保存。
        val edit = pendingEdit ?: return@LaunchedEffect
        delay(timeMillis = 450)
        if (edit != savedValue || submittedEdits.isNotEmpty()) {
            submittedEdits.addLast(edit)
            save(edit)
        }
        pendingEdit = null
    }
    val placeholderContent: (@Composable () -> Unit)? = placeholder?.let { placeholderText ->
        { Text(placeholderText) }
    }

    RpScrollableOutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = draft,
        onValueChange = { edit ->
            draft = edit
            pendingEdit = edit
        },
        label = { Text(label) },
        placeholder = placeholderContent,
        alwaysMinimizeLabel = alwaysMinimizeLabel,
        minLines = minLines,
        maxLines = maxLines,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
    )
}
