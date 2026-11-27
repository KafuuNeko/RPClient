package me.kafuuneko.rpclient.feature.agent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitDraft
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitField
import me.kafuuneko.rpclient.feature.agent.model.AgentSceneRevisionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentSettingsDialog
import me.kafuuneko.rpclient.feature.agent.model.AgentToolItem
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup
import me.kafuuneko.rpclient.ui.agent.titleRes
import me.kafuuneko.rpclient.ui.dialog.AppDialogScaffold
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.RpCheckBadge
import me.kafuuneko.rpclient.ui.widgets.RpNumberStepper
import me.kafuuneko.rpclient.ui.widgets.RpPercentageSlider
import me.kafuuneko.rpclient.ui.widgets.draggableLazyListScrollIndicator

/**
 * 统一分发 Agent 设置页的工具选择、运行限额与剧情状态弹窗。
 * - 由单聊与群聊设置页顶层挂载，避免随 LazyColumn 滚出视口而被意外卸载。
 *
 * @param state 当前会话的 Agent 状态树。
 * @param emit 宿主页面的 Agent 意图发送回调。
 */
@Composable
fun AgentSettingsDialogHost(
    state: AgentUiState,
    emit: (AgentUiAction) -> Unit,
) {
    // 按互斥弹窗状态分发，确认与取消统一经 emit 回传给协调器。
    when (val dialog = state.settingsDialog) {
        AgentSettingsDialog.None -> Unit

        is AgentSettingsDialog.Tools -> AgentToolsDialog(
            tools = state.tools,
            selectedToolIds = dialog.selectedToolIds,
            memoryReadOnly = state.memoryReadOnly,
            emit = emit,
        )

        is AgentSettingsDialog.Limits -> AgentLimitsDialog(
            draft = dialog.draft,
            errorRes = dialog.errorRes,
            emit = emit,
        )

        AgentSettingsDialog.Scene -> AgentSceneStateDialog(
            state = state,
            emit = emit,
        )
    }
}

/** 分组展示当前会话可用的本地工具，支持全选/清空并一次性提交保存。 */
@Composable
private fun AgentToolsDialog(
    tools: List<AgentToolItem>,
    selectedToolIds: Set<String>,
    memoryReadOnly: Boolean,
    emit: (AgentUiAction) -> Unit,
) {
    // 按统一领域分组整理候选工具，并计算全选切换状态。
    val groupedTools = remember(tools) {
        ToolGroup.entries.mapNotNull { group ->
            val items = tools.filter { it.group == group }
            if (items.isEmpty()) null else group to items
        }
    }
    val allSelected = tools.isNotEmpty() && selectedToolIds.size == tools.size

    AppDialogScaffold(
        onDismissRequest = { emit(AgentUiAction.DismissSettingsDialog) },
        title = stringResource(R.string.agent_select_tools),
        subtitle = stringResource(
            R.string.agent_tools_enabled_count,
            selectedToolIds.size,
            tools.size,
        ),
        subtitleMaxLines = 1,
        badgeIcon = Icons.Rounded.Build,
        compactHeader = true,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { emit(AgentUiAction.SaveTools) },
        onDismiss = { emit(AgentUiAction.DismissSettingsDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { emit(AgentUiAction.SetAllDraftTools(enabled = !allSelected)) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (allSelected) R.string.agent_clear_all_tools else R.string.agent_select_all_tools,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            AgentToolsList(
                groupedTools = groupedTools,
                selectedToolIds = selectedToolIds,
                memoryReadOnly = memoryReadOnly,
                emit = emit,
            )
        }
    }
}

/** 惰性渲染按领域分组的工具多选列表。 */
@Composable
private fun AgentToolsList(
    groupedTools: List<Pair<ToolGroup, List<AgentToolItem>>>,
    selectedToolIds: Set<String>,
    memoryReadOnly: Boolean,
    emit: (AgentUiAction) -> Unit,
) {
    // 限制弹窗内列表高度并附带滚动条，避免工具项增多后挤占底部按钮。
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .draggableLazyListScrollIndicator(listState),
        contentPadding = PaddingValues(end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groupedTools.forEach { (group, groupTools) ->
            item(key = "group-${group.name}") {
                Text(
                    text = stringResource(group.titleRes()),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp),
                )
            }
            items(items = groupTools, key = { it.id }) { tool ->
                AgentToolPickerRow(
                    tool = tool,
                    selected = tool.id in selectedToolIds,
                    blockedByReadOnly = memoryReadOnly && tool.writesMemory,
                    onToggle = { emit(AgentUiAction.ToggleDraftTool(tool.id)) },
                )
            }
        }
    }
}

/** 展示单个工具的选择卡片，启用只读记忆时标明写入类工具暂不生效。 */
@Composable
private fun AgentToolPickerRow(
    tool: AgentToolItem,
    selected: Boolean,
    blockedByReadOnly: Boolean,
    onToggle: () -> Unit,
) {
    // 按选中态切换高亮边框，对只读拦截、全局停用及缺配置给出明确辅助提示。
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            },
        ),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val dimmed = blockedByReadOnly || tool.globallyDisabled
                Text(
                    text = tool.label ?: stringResource(tool.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (blockedByReadOnly) {
                    Text(
                        text = stringResource(R.string.agent_tool_read_only_blocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (tool.globallyDisabled) {
                    Text(
                        text = stringResource(R.string.agent_tool_globally_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (tool.needsConfiguration) {
                    Text(
                        text = stringResource(R.string.tool_settings_needs_configuration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            RpCheckBadge(selected = selected)
        }
    }
}

/** 使用统一数值步进器与百分比滑块调整 Agent 单次运行的限额与压缩水位。 */
@Composable
private fun AgentLimitsDialog(
    draft: AgentLimitDraft,
    errorRes: Int?,
    emit: (AgentUiAction) -> Unit,
) {
    // 弹窗仅渲染草稿并回传字段变更，具体边界校验在保存时由协调器统一执行。
    AppDialogScaffold(
        onDismissRequest = { emit(AgentUiAction.DismissSettingsDialog) },
        title = stringResource(R.string.agent_limits_title),
        subtitle = stringResource(R.string.agent_limits_desc),
        badgeIcon = Icons.Rounded.Tune,
        compactHeader = true,
        scrollableContent = true,
        confirmText = stringResource(R.string.save),
        dismissText = stringResource(R.string.cancel),
        onConfirm = { emit(AgentUiAction.SaveLimits) },
        onDismiss = { emit(AgentUiAction.DismissSettingsDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AgentLimitStepperFields(draft = draft, emit = emit)
            RpPercentageSlider(
                title = stringResource(R.string.agent_limit_watermark),
                value = draft.watermark.toIntOrNull() ?: AgentLimitDraft.DEFAULT_WATERMARK,
                helper = stringResource(R.string.agent_limit_watermark_desc),
                valueRange = 10..100,
                onValueChange = { value ->
                    emit(AgentUiAction.ChangeLimit(AgentLimitField.Watermark, value.toString()))
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { emit(AgentUiAction.ResetDefaultLimits) }) {
                    Text(stringResource(R.string.agent_reset_defaults))
                }
            }
            errorRes?.let { resId ->
                Text(
                    text = stringResource(resId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 渲染轮次、调用次数、模型请求、Token 与主动耗时五项限额步进器。 */
@Composable
private fun AgentLimitStepperFields(
    draft: AgentLimitDraft,
    emit: (AgentUiAction) -> Unit,
) {
    // 轮次、工具调用与请求次数步进项。
    RpNumberStepper(
        title = stringResource(R.string.agent_limit_rounds),
        value = draft.rounds,
        min = 1,
        max = 50,
        step = 1,
        onValueChange = { value -> emit(AgentUiAction.ChangeLimit(AgentLimitField.Rounds, value)) },
    )
    RpNumberStepper(
        title = stringResource(R.string.agent_limit_calls),
        value = draft.calls,
        min = 1,
        max = 200,
        step = 1,
        onValueChange = { value -> emit(AgentUiAction.ChangeLimit(AgentLimitField.Calls, value)) },
    )
    RpNumberStepper(
        title = stringResource(R.string.agent_limit_requests),
        value = draft.requests,
        min = 1,
        max = 50,
        step = 1,
        onValueChange = { value ->
            emit(AgentUiAction.ChangeLimit(AgentLimitField.Requests, value))
        },
    )
    // Token 预算与主动耗时步进项。
    RpNumberStepper(
        title = stringResource(R.string.agent_limit_tokens),
        value = draft.tokens,
        min = 1_000,
        max = 2_000_000,
        step = 4_000,
        onValueChange = { value -> emit(AgentUiAction.ChangeLimit(AgentLimitField.Tokens, value)) },
    )
    RpNumberStepper(
        title = stringResource(R.string.agent_limit_seconds),
        value = draft.seconds,
        min = 10,
        max = 3_600,
        step = 30,
        onValueChange = { value ->
            emit(AgentUiAction.ChangeLimit(AgentLimitField.Seconds, value))
        },
    )
}

/** 展示格式化后的当前剧情状态、近期修订差异与撤销入口。 */
@Composable
private fun AgentSceneStateDialog(
    state: AgentUiState,
    emit: (AgentUiAction) -> Unit,
) {
    // 仅存在可回退修订时提供撤销主按钮，否则将主操作收拢为关闭。
    AppDialogScaffold(
        onDismissRequest = { emit(AgentUiAction.DismissSettingsDialog) },
        title = stringResource(R.string.agent_scene_title),
        subtitle = stringResource(R.string.agent_scene_revision, state.sceneRevision),
        subtitleMaxLines = 1,
        badgeIcon = Icons.Rounded.AutoStories,
        compactHeader = true,
        scrollableContent = true,
        confirmText = stringResource(
            if (state.canUndoScene) R.string.agent_undo_scene else R.string.close,
        ),
        dismissText = if (state.canUndoScene) stringResource(R.string.close) else null,
        onConfirm = {
            if (state.canUndoScene) {
                emit(AgentUiAction.UndoScene)
            } else {
                emit(AgentUiAction.DismissSettingsDialog)
            }
        },
        onDismiss = if (state.canUndoScene) {
            { emit(AgentUiAction.DismissSettingsDialog) }
        } else {
            null
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 520.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AgentSceneSnapshotSection(sceneFormatted = state.sceneFormatted)
            if (state.sceneHistory.isNotEmpty()) {
                AgentSceneHistorySection(sceneHistory = state.sceneHistory)
            }
        }
    }
}

/** 展示当前已格式化的剧情 JSON 快照或空状态提示。 */
@Composable
private fun AgentSceneSnapshotSection(sceneFormatted: String) {
    // 空快照显示引导文案，有内容时允许用户选中复制。
    Text(
        text = stringResource(R.string.agent_scene_current),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    if (sceneFormatted.isBlank()) {
        Text(
            text = stringResource(R.string.agent_scene_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            SelectionContainer {
                Text(
                    text = sceneFormatted,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** 展示近期剧情状态修订记录及对应键级差异。 */
@Composable
private fun AgentSceneHistorySection(
    sceneHistory: List<AgentSceneRevisionItem>,
) {
    // 倒序展示最近修订条目，无键变化时使用短横线占位。
    Text(
        text = stringResource(R.string.agent_scene_history),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    sceneHistory.forEach { revision ->
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.agent_scene_revision,
                        revision.revision
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = revision.difference.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
