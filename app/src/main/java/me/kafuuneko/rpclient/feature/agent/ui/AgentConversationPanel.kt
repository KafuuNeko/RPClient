package me.kafuuneko.rpclient.feature.agent.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentPresetItem
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentStepItem
import me.kafuuneko.rpclient.feature.agent.model.AgentStepKind
import me.kafuuneko.rpclient.feature.agent.model.AgentTaskItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallStatus
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.feature.agent.model.AgentWriteDisposition
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.appearance.PageSurfaceKind
import me.kafuuneko.rpclient.ui.theme.AppTheme
import me.kafuuneko.rpclient.ui.widgets.AppSurface
import me.kafuuneko.rpclient.ui.widgets.LocalPageBubbleOpacity
import me.kafuuneko.rpclient.ui.widgets.PageButton
import me.kafuuneko.rpclient.ui.widgets.PageFilterChip
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedButton
import me.kafuuneko.rpclient.ui.widgets.PageOutlinedTextField
import me.kafuuneko.rpclient.ui.widgets.PageSurface
import me.kafuuneko.rpclient.ui.widgets.PageSwitch
import me.kafuuneko.rpclient.ui.widgets.SettingsSection
import me.kafuuneko.rpclient.ui.widgets.generalContentBackground
import me.kafuuneko.rpclient.ui.widgets.pageFilterChipColors

/**
 * 会话独立 Agent 设置分区，沿用单聊/群聊设置页的毛玻璃控件与弹窗交互体系。
 * - 关闭时仅展示紧凑的主开关行，不占据首屏高频操作区。
 * - 开启后提供预设下拉菜单、记忆权限开关，以及工具、运行限额与剧情状态三个独立弹窗入口。
 *
 * @param state 当前会话的 Agent 状态树。
 * @param emit 宿主页面的 Agent 意图发送回调。
 */
@Composable
fun AgentSettingsPanel(state: AgentUiState, emit: (AgentUiAction) -> Unit) {
    SettingsSection(title = stringResource(R.string.agent_title)) {
        AgentSettingsSwitchRow(
            title = stringResource(R.string.agent_enable),
            subtitle = stringResource(R.string.agent_enable_desc),
            checked = state.enabled,
            onCheckedChange = { enabled -> emit(AgentUiAction.ToggleEnabled(enabled)) },
        )

        AnimatedVisibility(
            visible = state.enabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                )
                AgentPresetDropdown(
                    presets = state.presets,
                    selectedPresetId = state.selectedPresetId,
                    onSelect = { presetId -> emit(AgentUiAction.SelectPreset(presetId)) },
                )
                AgentSettingsSwitchRow(
                    title = stringResource(R.string.agent_memory_read_only),
                    subtitle = stringResource(R.string.agent_memory_read_only_desc),
                    checked = state.memoryReadOnly,
                    onCheckedChange = { enabled -> emit(AgentUiAction.ToggleMemoryReadOnly(enabled)) },
                )
                // 开启只读记忆时禁用自动提升开关，避免相互矛盾的设置状态。
                AgentSettingsSwitchRow(
                    title = stringResource(R.string.agent_auto_promote),
                    subtitle = stringResource(R.string.agent_auto_promote_desc),
                    checked = state.autoPromoteMemory,
                    enabled = !state.memoryReadOnly,
                    onCheckedChange = { enabled -> emit(AgentUiAction.ToggleAutoPromote(enabled)) },
                )
                val enabledToolsCount = state.tools.count { it.enabled }
                val totalToolsCount = state.tools.size
                AgentSettingsEntryRow(
                    icon = Icons.Rounded.Build,
                    title = stringResource(R.string.agent_select_tools),
                    value = "$enabledToolsCount / $totalToolsCount",
                    onClick = { emit(AgentUiAction.OpenToolsDialog) },
                )
                val rounds = state.limits.rounds.toIntOrNull() ?: 5
                val calls = state.limits.calls.toIntOrNull() ?: 20
                val watermark = state.limits.watermark.toIntOrNull() ?: 80
                AgentSettingsEntryRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.agent_limits_title),
                    value = stringResource(R.string.agent_limits_summary, rounds, calls, watermark),
                    onClick = { emit(AgentUiAction.OpenLimitsDialog) },
                )
                AgentSettingsEntryRow(
                    icon = Icons.Rounded.AutoStories,
                    title = stringResource(R.string.agent_scene_title),
                    value = stringResource(R.string.agent_scene_revision, state.sceneRevision),
                    onClick = { emit(AgentUiAction.OpenSceneDialog) },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.agent_model_capability_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
        }

        state.errorRes?.let { errorRes ->
            Text(
                text = stringResource(errorRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 在输入栏上方仅承接等待用户回答或写入确认的挂起任务。
 * - 运行中的任务直接渲染为消息列表内的流式角色气泡，不在此处重复占位。
 * - 已取消、失败或过期的无消息运行不常驻遮挡对话区。
 */
@Composable
fun AgentPendingTasks(state: AgentUiState, emit: (AgentUiAction) -> Unit) {
    val waitingTask = state.tasks.firstOrNull { it.messageId == null && it.waiting } ?: return
    if (state.question?.runId != waitingTask.id) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        AgentTaskCard(
            task = waitingTask,
            state = state,
            emit = emit,
            embeddedInBubble = false,
        )
    }
}

/** 共用气泡内的任务过程卡，展开选择由运行 ID 在页面 UiState 中持有。 */
@Composable
fun AgentMessageTasks(messageId: Long, state: AgentUiState, emit: (AgentUiAction) -> Unit) {
    state.tasks
        .filter { it.messageId == messageId }
        .forEach { task ->
            AgentTaskCard(
                task = task,
                state = state,
                emit = emit,
                embeddedInBubble = true,
            )
        }
}

/**
 * 渲染可折叠的 Agent 任务过程卡，视觉样式与消息内思考块保持一致。
 * - 嵌入气泡时继承气泡半透明底色，避免不透明深色块破坏毛玻璃层次。
 * - 仅展示步骤、工具调用状态、用量与思考摘要，不重复拼接最终正文。
 * - 仅在等待用户输入或确认时展示操作区与取消入口，运行中的停止统一由输入栏停止按钮控制。
 *
 * @param task 当前任务摘要。
 * @param state 页面持有的 Agent 状态树。
 * @param emit Agent 交互动作分发回调。
 * @param embeddedInBubble 是否嵌入在消息气泡内部。
 */
@Composable
fun AgentTaskCard(
    task: AgentTaskItem,
    state: AgentUiState,
    embeddedInBubble: Boolean = true,
    emit: (AgentUiAction) -> Unit,
) {
    val expanded = task.id in state.expandedRunIds
    val question = state.question?.takeIf { it.runId == task.id }
    val bubbleOpacity = LocalPageBubbleOpacity.current
    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AgentTaskCardHeader(
                task = task,
                expanded = expanded,
                onToggle = { emit(AgentUiAction.ToggleTask(task.id)) },
            )
            task.reasonRes?.let { reasonRes ->
                Text(
                    text = stringResource(reasonRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                AgentTaskExpandedDetails(task = task)
            }
            // 等待态快捷交互始终可见，用户无需展开步骤列表即可回答或确认。
            if (question != null) {
                AgentQuestionSection(
                    question = question,
                    answerDraft = state.answerDraft,
                    emit = emit,
                )
            }
        }
    }

    if (embeddedInBubble) {
        val surfaceAlpha = (if (task.active) 0.50f else 0.36f) * bubbleOpacity
        AppSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = surfaceAlpha),
            content = cardContent,
        )
    } else {
        PageSurface(
            kind = PageSurfaceKind.Bubble,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f * bubbleOpacity),
            border = BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.35f * bubbleOpacity.coerceAtLeast(
                        0.3f
                    )
                ),
            ),
            content = cardContent,
        )
    }
}

/** 渲染单行折叠头，实时反映当前活跃工具或已完成调用数。 */
@Composable
private fun AgentTaskCardHeader(
    task: AgentTaskItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val summary = agentTaskHeaderSummary(task)
    val isRunning = task.active && !task.waiting
    val titleColor = when {
        isRunning || task.waiting -> MaterialTheme.colorScheme.primary
        task.runState == AgentRunState.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            isRunning -> CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )

            task.waiting -> Icon(
                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )

            task.runState == AgentRunState.Completed -> Icon(
                imageVector = if (task.toolCalls > 0) Icons.Rounded.Build else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                modifier = Modifier.size(15.dp),
            )

            else -> Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (task.runState == AgentRunState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = summary,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(if (expanded) R.string.agent_collapse else R.string.agent_expand),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 计算单行折叠摘要：优先展示当前正在执行的工具名称或已完成工具次数。 */
@Composable
private fun agentTaskHeaderSummary(task: AgentTaskItem): String {
    if (task.active && !task.waiting) {
        val runningTool = task.steps
            .lastOrNull { it.kind == AgentStepKind.Tools }
            ?.toolCalls
            ?.lastOrNull { it.status == AgentToolCallStatus.Running }
            ?: task.steps.lastOrNull { it.kind == AgentStepKind.Tools && !it.completed }?.toolCalls?.lastOrNull()
        if (runningTool != null) {
            return stringResource(
                R.string.agent_task_running_tool,
                stringResource(runningTool.labelRes)
            )
        }
        if (task.steps.lastOrNull()
                ?.let { it.kind == AgentStepKind.Compaction && !it.completed } == true
        ) {
            return stringResource(R.string.agent_step_compaction)
        }
        return stringResource(
            R.string.agent_task_status,
            stringResource(task.statusRes),
            task.steps.size.coerceAtLeast(1),
        )
    }
    if (task.runState == AgentRunState.Completed) {
        return if (task.toolCalls > 0) {
            stringResource(R.string.agent_task_completed_tools, task.toolCalls)
        } else {
            stringResource(
                R.string.agent_task_completed_no_tools,
                task.modelRequests.coerceAtLeast(1)
            )
        }
    }
    return stringResource(
        R.string.agent_task_status,
        stringResource(task.statusRes),
        task.steps.size,
    )
}

/** 渲染展开后的用量统计、结构化步骤与工具明细，以及独立的思考过程摘要。 */
@Composable
private fun AgentTaskExpandedDetails(task: AgentTaskItem) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                R.string.agent_usage,
                task.modelRequests,
                task.toolCalls,
                task.tokens,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.80f),
        )
        if (task.steps.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                task.steps.forEach { step ->
                    AgentStepTimelineRows(step = step)
                }
            }
        }
        if (task.reasoning.isNotBlank()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.thought_process),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = task.reasoning,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 将单个运行步骤渲染为清晰的时间线项；工具步骤展开展示每个具体工具的名称与结果。 */
@Composable
private fun AgentStepTimelineRows(step: AgentStepItem) {
    if (step.kind == AgentStepKind.Tools && step.toolCalls.isNotEmpty()) {
        step.toolCalls.forEach { call ->
            AgentToolCallRow(call = call)
        }
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (step.completed) {
            Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                modifier = Modifier.size(14.dp),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = stringResource(step.kindRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        step.tokens?.takeIf { it > 0L }?.let { tokens ->
            Text(
                text = formatCompactTokens(tokens),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
        }
    }
}

/** 渲染单个工具调用的本地化名称与状态徽标。 */
@Composable
private fun AgentToolCallRow(call: AgentToolCallItem) {
    val (statusLabelRes, statusColor) = when (call.status) {
        AgentToolCallStatus.Running -> R.string.agent_tool_status_running to MaterialTheme.colorScheme.primary
        AgentToolCallStatus.Failed -> R.string.agent_tool_status_failed to MaterialTheme.colorScheme.error
        AgentToolCallStatus.WaitingForUser -> R.string.agent_tool_status_waiting to MaterialTheme.colorScheme.tertiary
        AgentToolCallStatus.Unknown -> R.string.agent_tool_status_unknown to MaterialTheme.colorScheme.onSurfaceVariant
        AgentToolCallStatus.Completed -> when (call.writeDisposition) {
            AgentWriteDisposition.Committed -> R.string.agent_tool_write_committed to MaterialTheme.colorScheme.primary
            AgentWriteDisposition.Proposed -> R.string.agent_tool_write_proposed to MaterialTheme.colorScheme.secondary
            null -> R.string.agent_tool_status_completed to MaterialTheme.colorScheme.primary
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (call.status) {
            AgentToolCallStatus.Running -> CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.6.dp,
                color = statusColor,
            )

            AgentToolCallStatus.Failed -> Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp),
            )

            AgentToolCallStatus.WaitingForUser -> Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp),
            )

            else -> Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = stringResource(call.labelRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AppSurface(
            shape = RoundedCornerShape(6.dp),
            color = statusColor.copy(alpha = 0.14f),
        ) {
            Text(
                text = stringResource(statusLabelRes),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** 渲染等待用户提问或写入确认的快捷交互区。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentQuestionSection(
    question: AgentQuestionItem,
    answerDraft: String,
    emit: (AgentUiAction) -> Unit,
) {
    val bubbleOpacity = LocalPageBubbleOpacity.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        )
        if (question.confirmation) {
            Text(
                text = stringResource(R.string.agent_confirm_write),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (question.details.isNotBlank()) {
                AppSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f * bubbleOpacity),
                ) {
                    Text(
                        text = question.details,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PageOutlinedButton(
                    onClick = { emit(AgentUiAction.Cancel) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PageButton(
                    onClick = { emit(AgentUiAction.SubmitAnswer(true)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else {
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (question.options.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    question.options.forEach { option ->
                        PageFilterChip(
                            selected = answerDraft == option,
                            onClick = { emit(AgentUiAction.ChangeAnswer(option)) },
                            label = { Text(option) },
                            shape = RoundedCornerShape(10.dp),
                            colors = pageFilterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.75f
                                ),
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
            PageOutlinedTextField(
                value = answerDraft,
                onValueChange = { value -> emit(AgentUiAction.ChangeAnswer(value)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.agent_answer)) },
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PageOutlinedButton(
                    onClick = { emit(AgentUiAction.Cancel) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PageButton(
                    enabled = answerDraft.isNotBlank(),
                    onClick = { emit(AgentUiAction.SubmitAnswer(false)) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.agent_resume),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 与 UserPersonaPicker 一致的下拉菜单样式，用于选择当前会话绑定的 Agent 预设。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentPresetDropdown(
    presets: List<AgentPresetItem>,
    selectedPresetId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val followActiveLabel = stringResource(R.string.agent_follow_active_preset)
    val selectedName = presets.firstOrNull { it.id == selectedPresetId }
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: followActiveLabel
    val shape = RoundedCornerShape(12.dp)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        PageOutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = shape,
            label = { Text(stringResource(R.string.agent_preset_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(
                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                    enabled = true,
                )
                .generalContentBackground(shape = shape, hasLabel = true),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                val isSelected = preset.id == selectedPresetId
                val label = preset.name.ifBlank { followActiveLabel }
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = isSelected },
                    text = { Text(label) },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(preset.id)
                    },
                )
            }
        }
    }
}

/** 与设置页统一的毛玻璃开关行，使用 PageSwitch 适配背景透明度。 */
@Composable
private fun AgentSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.40f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) })
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
        PageSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** 与 SettingsActionRow 外观一致的子配置弹窗入口行，尾部展示摘要标签与箭头。 */
@Composable
private fun AgentSettingsEntryRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppSurface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 将步骤 Token 估算压缩为紧凑可读文本。 */
private fun formatCompactTokens(tokens: Long): String {
    return if (tokens >= 1000L) {
        val compact = tokens / 1000.0
        String.format(Locale.US, "%.1fk Tk", compact)
    } else {
        "$tokens Tk"
    }
}

@Preview(widthDp = 360, showBackground = true)
@Composable
private fun AgentTaskCardExpandedPreview() {
    AppTheme(darkTheme = true, dynamicColor = false) {
        val task = AgentTaskItem(
            id = "run-1",
            messageId = 10L,
            statusRes = R.string.agent_state_completed,
            active = false,
            waiting = false,
            modelRequests = 2,
            toolCalls = 2,
            tokens = 14800L,
            steps = listOf(
                AgentStepItem(
                    kindRes = R.string.agent_step_model,
                    completed = true,
                    kind = AgentStepKind.Model,
                    tokens = 7200L,
                ),
                AgentStepItem(
                    kindRes = R.string.agent_step_tools,
                    completed = true,
                    kind = AgentStepKind.Tools,
                    toolCalls = listOf(
                        AgentToolCallItem(
                            id = "c1",
                            toolId = "search_memory",
                            labelRes = R.string.agent_tool_search_memory,
                            status = AgentToolCallStatus.Completed,
                        ),
                        AgentToolCallItem(
                            id = "c2",
                            toolId = "upsert_memory",
                            labelRes = R.string.agent_tool_upsert_memory,
                            status = AgentToolCallStatus.Failed,
                        ),
                    ),
                ),
                AgentStepItem(
                    kindRes = R.string.agent_step_model,
                    completed = true,
                    kind = AgentStepKind.Model,
                    tokens = 7600L,
                ),
            ),
            reasoning = "Check existing memory before answering in character.",
            runState = AgentRunState.Completed,
        )
        Column(modifier = Modifier.padding(12.dp)) {
            AgentTaskCard(
                task = task,
                state = AgentUiState(
                    enabled = true,
                    tasks = listOf(task),
                    expandedRunIds = setOf("run-1"),
                ),
                emit = {},
            )
        }
    }
}
