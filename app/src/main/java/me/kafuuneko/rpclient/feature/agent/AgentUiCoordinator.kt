package me.kafuuneko.rpclient.feature.agent

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import kotlinx.coroutines.flow.first
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitDraft
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitField
import me.kafuuneko.rpclient.feature.agent.model.AgentPresetItem
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentSceneRevisionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentSettingsDialog
import me.kafuuneko.rpclient.feature.agent.model.AgentStepItem
import me.kafuuneko.rpclient.feature.agent.model.AgentStepKind
import me.kafuuneko.rpclient.feature.agent.model.AgentTaskItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallStatus
import me.kafuuneko.rpclient.feature.agent.model.AgentToolGroup
import me.kafuuneko.rpclient.feature.agent.model.AgentToolItem
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.feature.agent.model.AgentWriteDisposition
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.runtime.AgentProgressStore
import me.kafuuneko.rpclient.libs.agent.runtime.AgentRunManager
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository

/**
 * 两种聊天页面共用的 Agent 状态映射及设置操作，不持有页面或 Compose 状态。
 * - 本地展开态、回答草稿与设置子弹窗状态均由宿主 UiState 保留。
 * - 流式正文预览仅供给活动气泡正文，已完成任务只保留步骤、工具调用与思考摘要，避免重复展示正文。
 * - 修改策略先保存并撤权，再取消活动任务，旧表单不会恢复运行资格。
 */
class AgentUiCoordinator(
    private val mRuns: AgentRunRepository,
    private val mManager: () -> AgentRunManager,
    private val mChats: ChatRepository,
    private val mGroups: GroupChatRepository,
    private val mPresets: PromptPresetRepository,
    private val mScenes: SceneStateRepository,
    private val mProgress: AgentProgressStore,
) {
    private val mPrettyGson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /** 返回群聊批次成本约束供外层调度使用。 */
    suspend fun policy(conversation: ConversationRef) = mRuns.policy(conversation)

    /** 观察独立于日志的运行元数据，界面只订阅低频步骤和成本变化。 */
    fun observe(conversation: ConversationRef) = mRuns.observe(conversation)

    /** 流式预览只更新内存模型，不为每片正文重新读取 Room。 */
    fun progress(conversation: ConversationRef) = mProgress.observe(conversation)

    /** 只合并当前任务的实时正文与思考摘要，已完成任务不回填重复正文。 */
    fun withProgress(conversation: ConversationRef, state: AgentUiState): AgentUiState {
        return state.copy(
            tasks = state.tasks.map { task ->
                val progress = mProgress.get(conversation, task.id) ?: return@map task
                task.copy(
                    liveText = if (task.active) progress.text else "",
                    reasoning = progress.reasoning,
                )
            },
        )
    }

    /**
     * 从权威策略、步骤和工具调用摘要构建面板，保留页面展开态及尚未提交的输入草稿。
     *
     * @param conversation 当前单聊或群聊身份引用。
     * @param previous 页面已持有的 Agent 状态快照。
     * @param settings 是否一并加载设置页所需的预设、工具列表与剧情状态。
     * @return 更新后的完整 Agent 状态树。
     */
    suspend fun load(
        conversation: ConversationRef,
        previous: AgentUiState,
        settings: Boolean = previous.settingsLoaded,
    ): AgentUiState {
        // 读取会话级别的 Agent 开关与预设绑定。
        val enabled: Boolean
        val preset: Long?
        when (conversation) {
            is ConversationRef.Single -> {
                val session = mChats.getSessionById(conversation.id) ?: return AgentUiState()
                enabled = session.agentEnabled
                preset = session.agentPromptPresetId
            }

            is ConversationRef.Group -> {
                val session = mGroups.getSessionById(conversation.id) ?: return AgentUiState()
                enabled = session.agentEnabled
                preset = session.agentPromptPresetId
            }
        }
        // 读取运行摘要，并将每个步骤关联到具体工具调用状态。
        val runs = mRuns.observe(conversation).first()
        val tasks = runs.map { run ->
            val invocationsByStep = mRuns.invocations(run.id).groupBy { it.stepId }
            val steps = mRuns.steps(run.id).map { step ->
                val kind = when (step.kind) {
                    "Tools" -> AgentStepKind.Tools
                    "Compaction" -> AgentStepKind.Compaction
                    else -> AgentStepKind.Model
                }
                val kindRes = when (kind) {
                    AgentStepKind.Tools -> R.string.agent_step_tools
                    AgentStepKind.Compaction -> R.string.agent_step_compaction
                    AgentStepKind.Model -> R.string.agent_step_model
                }
                val stepTools = invocationsByStep[step.id].orEmpty().map(::toToolCallItem)
                AgentStepItem(
                    kindRes = kindRes,
                    completed = step.completedAt != null,
                    kind = kind,
                    tokens = step.actualTokens,
                    toolCalls = stepTools,
                )
            }
            val progress = mProgress.get(conversation, run.id)
            val isActive = !run.state.isTerminal
            AgentTaskItem(
                id = run.id,
                messageId = run.messageId,
                statusRes = status(run.state),
                active = isActive,
                waiting = run.state == AgentRunState.WaitingForUser,
                modelRequests = run.modelRequests,
                toolCalls = run.toolCalls,
                tokens = run.totalTokens,
                steps = steps,
                liveText = if (isActive) progress?.text.orEmpty() else "",
                reasoning = progress?.reasoning.orEmpty(),
                reasonRes = R.string.agent_repeated_reads.takeIf { run.outcomeCode == "repeated_reads" },
                characterId = run.characterId,
                runState = run.state,
            )
        }
        // 仅当存在等待态运行时读取本机挂起问题或确认摘要。
        val waiting = if (runs.any { it.state == AgentRunState.WaitingForUser }) {
            mManager().waiting(conversation)
        } else {
            null
        }
        val activeRunId = runs.firstOrNull { !it.state.isTerminal }?.id
        val question = waiting?.takeIf { activeRunId != null }?.let { result ->
            AgentQuestionItem(
                runId = requireNotNull(activeRunId),
                question = result.question,
                options = result.options,
                confirmation = result.kind != ToolUserRequestKind.Input,
                details = result.details.entrySet()
                    .filter { it.key != "operation" }
                    .joinToString("\n") { entry ->
                        val value = entry.value
                        if (value.isJsonPrimitive) value.asString else value.toString()
                    },
            )
        }
        val base = previous.copy(
            enabled = enabled,
            tasks = tasks,
            question = question,
            selectedPresetId = preset,
        )
        if (!settings) return base
        // 加载设置页所需的工具清单、预设列表、成本限额与剧情状态修订历史。
        val policy = mRuns.policy(conversation)
        val selected = mRuns.enabledTools(policy)
        val scene = mScenes.get(conversation)
        val revisions = mScenes.revisions(conversation)
        val history = revisions.takeLast(5).map { revision ->
            val previousJson =
                revisions.lastOrNull { it.revision < revision.revision }?.stateJson ?: "{}"
            AgentSceneRevisionItem(
                revision = revision.revision,
                difference = sceneDifference(previousJson, revision.stateJson),
            )
        }.reversed()
        val presets = listOf(AgentPresetItem(id = null, name = "")) +
            mPresets.observePresets().first()
                .filter { it.preset.id > 0 }
                .map { AgentPresetItem(id = it.preset.id, name = it.preset.name) }
        return base.copy(
            settingsLoaded = true,
            tools = TOOL_SPECS.map { spec ->
                AgentToolItem(
                    id = spec.id,
                    labelRes = spec.labelRes,
                    group = spec.group,
                    enabled = spec.id in selected,
                    writesMemory = spec.writesMemory,
                )
            },
            presets = presets,
            memoryReadOnly = policy.memoryReadOnly,
            autoPromoteMemory = policy.autoPromoteMemory,
            limits = AgentLimitDraft(
                rounds = policy.maxToolRounds.toString(),
                calls = policy.maxToolCalls.toString(),
                requests = policy.maxModelRequests.toString(),
                tokens = policy.maxTotalTokens.toString(),
                seconds = (policy.maxActiveMillis / 1000).toString(),
                watermark = policy.compactionWatermarkPercent.toString(),
            ),
            scene = scene.stateJson,
            sceneFormatted = formatSceneJson(scene.stateJson),
            sceneRevision = scene.revision,
            canUndoScene = scene.revision > 0,
            sceneHistory = history,
        )
    }

    /** 不涉及持久化的展开、弹窗打开关闭与草稿变更；返回空表示需要执行领域操作。 */
    fun local(state: AgentUiState, action: AgentUiAction): AgentUiState? = when (action) {
        AgentUiAction.OpenToolsDialog -> state.copy(
            settingsDialog = AgentSettingsDialog.Tools(
                selectedToolIds = state.tools.filter { it.enabled }
                    .mapTo(LinkedHashSet()) { it.id },
            ),
            errorRes = null,
        )

        is AgentUiAction.ToggleDraftTool -> {
            val dialog = state.settingsDialog as? AgentSettingsDialog.Tools ?: return state
            val nextSelected = if (action.id in dialog.selectedToolIds) {
                dialog.selectedToolIds - action.id
            } else {
                dialog.selectedToolIds + action.id
            }
            state.copy(settingsDialog = dialog.copy(selectedToolIds = nextSelected))
        }

        is AgentUiAction.SetAllDraftTools -> {
            val dialog = state.settingsDialog as? AgentSettingsDialog.Tools ?: return state
            val nextSelected = if (action.enabled) {
                state.tools.mapTo(LinkedHashSet()) { it.id }
            } else {
                emptySet()
            }
            state.copy(settingsDialog = dialog.copy(selectedToolIds = nextSelected))
        }

        AgentUiAction.OpenLimitsDialog -> state.copy(
            settingsDialog = AgentSettingsDialog.Limits(draft = state.limits),
            errorRes = null,
        )

        is AgentUiAction.ChangeLimit -> {
            val dialog = state.settingsDialog as? AgentSettingsDialog.Limits
            val source = dialog?.draft ?: state.limits
            val updated = when (action.field) {
                AgentLimitField.Rounds -> source.copy(rounds = action.value)
                AgentLimitField.Calls -> source.copy(calls = action.value)
                AgentLimitField.Requests -> source.copy(requests = action.value)
                AgentLimitField.Tokens -> source.copy(tokens = action.value)
                AgentLimitField.Seconds -> source.copy(seconds = action.value)
                AgentLimitField.Watermark -> source.copy(watermark = action.value)
            }
            if (dialog != null) {
                state.copy(settingsDialog = dialog.copy(draft = updated, errorRes = null))
            } else {
                state.copy(limits = updated, errorRes = null)
            }
        }

        AgentUiAction.ResetDefaultLimits -> {
            val dialog = state.settingsDialog as? AgentSettingsDialog.Limits ?: return state
            state.copy(
                settingsDialog = dialog.copy(
                    draft = AgentLimitDraft(),
                    errorRes = null,
                ),
            )
        }

        AgentUiAction.OpenSceneDialog -> state.copy(
            settingsDialog = AgentSettingsDialog.Scene,
            errorRes = null,
        )

        AgentUiAction.DismissSettingsDialog -> state.copy(
            settingsDialog = AgentSettingsDialog.None,
            errorRes = null,
        )

        is AgentUiAction.ToggleTask -> state.copy(
            expandedRunIds = if (action.runId in state.expandedRunIds) {
                state.expandedRunIds - action.runId
            } else {
                state.expandedRunIds + action.runId
            },
        )

        is AgentUiAction.ChangeAnswer -> state.copy(answerDraft = action.value.take(4000))

        else -> null
    }

    /**
     * 执行需要持久化或控制运行状态的操作；任何策略收紧都会先保存撤权，再取消活动运行。
     *
     * @param conversation 当前会话引用。
     * @param state 触发操作时的 Agent 状态。
     * @param action 待执行的持久化或运行控制动作。
     * @return 更新后的 Agent 状态。
     */
    suspend fun update(
        conversation: ConversationRef,
        state: AgentUiState,
        action: AgentUiAction,
    ): AgentUiState {
        // 取消等待或活动中的 Agent 运行。
        if (action == AgentUiAction.Cancel) {
            mManager().stop(conversation)
            return load(conversation, state)
        }
        // 撤销最近一次剧情状态修订并刷新历史视图。
        if (action == AgentUiAction.UndoScene) {
            mManager().stop(conversation)
            val revisions = mScenes.revisions(conversation)
            val current = mScenes.get(conversation)
            if (revisions.isEmpty()) return state
            val next = AgentJson.parse(
                revisions.getOrNull(revisions.lastIndex - 1)?.stateJson ?: "{}",
            ).asJsonObject
            val old = AgentJson.parse(current.stateJson).asJsonObject
            val changes = (old.keySet() + next.keySet()).map { key ->
                SceneStateChange(listOf(key), next[key])
            }
            mScenes.update(
                conversation = conversation,
                expectedRevision = current.revision,
                changes = changes,
                effectiveAfterMessageId = revisions.last().effectiveAfterMessageId,
            )
            return load(conversation, state, settings = true)
        }
        // 读取当前权威策略并按动作计算目标配置。
        var policy = mRuns.policy(conversation)
        var enabled = state.enabled
        var presetId = state.selectedPresetId
        var nextDialog = state.settingsDialog
        when (action) {
            is AgentUiAction.ToggleEnabled -> {
                enabled = action.enabled
                if (!enabled) nextDialog = AgentSettingsDialog.None
            }

            is AgentUiAction.SelectPreset -> presetId = action.id

            is AgentUiAction.ToggleMemoryReadOnly -> {
                policy = policy.copy(memoryReadOnly = action.enabled)
            }

            is AgentUiAction.ToggleAutoPromote -> {
                policy = policy.copy(autoPromoteMemory = action.enabled)
            }

            AgentUiAction.SaveTools -> {
                val dialog = state.settingsDialog as? AgentSettingsDialog.Tools ?: return state
                val orderedValidIds =
                    TOOL_SPECS.map { it.id }.filter { it in dialog.selectedToolIds }
                policy = policy.copy(
                    enabledToolIdsJson = JsonArray().apply { orderedValidIds.forEach(::add) }
                        .toString(),
                )
                nextDialog = AgentSettingsDialog.None
            }

            AgentUiAction.SaveLimits -> {
                val dialog = state.settingsDialog as? AgentSettingsDialog.Limits
                val draft = dialog?.draft ?: state.limits
                val parsed = runCatching {
                    policy.copy(
                        maxToolRounds = draft.rounds.trim().toInt(),
                        maxToolCalls = draft.calls.trim().toInt(),
                        maxModelRequests = draft.requests.trim().toInt(),
                        maxTotalTokens = draft.tokens.trim().toLong(),
                        maxActiveMillis = Math.multiplyExact(draft.seconds.trim().toLong(), 1000L),
                        compactionWatermarkPercent = draft.watermark.trim().toInt(),
                    )
                }.getOrNull()
                if (parsed == null) {
                    return if (dialog != null) {
                        state.copy(settingsDialog = dialog.copy(errorRes = R.string.agent_invalid_settings))
                    } else {
                        state.copy(errorRes = R.string.agent_invalid_settings)
                    }
                }
                policy = parsed
                nextDialog = AgentSettingsDialog.None
            }

            else -> return state
        }
        // 校验并原子保存策略，若限额越界则保留弹窗并就地提示。
        try {
            mRuns.saveSettings(conversation, enabled, presetId, policy)
        } catch (error: IllegalArgumentException) {
            val dialog = state.settingsDialog as? AgentSettingsDialog.Limits
            return if (dialog != null) {
                state.copy(settingsDialog = dialog.copy(errorRes = R.string.agent_invalid_settings))
            } else {
                state.copy(errorRes = R.string.agent_invalid_settings)
            }
        }
        mManager().stop(conversation)
        return load(
            conversation = conversation,
            previous = state.copy(settingsDialog = nextDialog, errorRes = null),
            settings = true,
        )
    }

    /** 新输入只取消确实存在的 Agent 占有者，普通路径无需初始化工具运行器。 */
    suspend fun stopActive(conversation: ConversationRef) {
        if (mRuns.active(conversation) != null) mManager().stop(conversation)
    }

    /** 页面离开时保留等待任务，普通页面无需创建运行器。 */
    suspend fun leaveActive(conversation: ConversationRef) {
        if (mRuns.active(conversation) != null) mManager().leave(conversation)
    }

    /** 将持久化工具调用映射为脱敏展示模型。 */
    private fun toToolCallItem(invocation: ToolInvocation): AgentToolCallItem {
        val labelRes = TOOL_LABELS[invocation.toolId] ?: R.string.agent_step_tools
        val status = when (invocation.state) {
            ToolInvocationState.Prepared, ToolInvocationState.Dispatching -> AgentToolCallStatus.Running
            ToolInvocationState.Completed -> AgentToolCallStatus.Completed
            ToolInvocationState.Failed -> AgentToolCallStatus.Failed
            ToolInvocationState.AwaitingUser -> AgentToolCallStatus.WaitingForUser
            ToolInvocationState.UnknownOutcome -> AgentToolCallStatus.Unknown
        }
        val disposition = when (invocation.writeDisposition) {
            ToolWriteDisposition.Committed.name -> AgentWriteDisposition.Committed
            ToolWriteDisposition.Proposed.name -> AgentWriteDisposition.Proposed
            else -> null
        }
        return AgentToolCallItem(
            id = invocation.id,
            toolId = invocation.toolId,
            labelRes = labelRes,
            status = status,
            writeDisposition = disposition,
        )
    }

    private fun status(state: AgentRunState): Int = when (state) {
        AgentRunState.WaitingForUser -> R.string.agent_state_waiting
        AgentRunState.Completed -> R.string.agent_state_completed
        AgentRunState.Cancelled -> R.string.agent_state_cancelled
        AgentRunState.Interrupted -> R.string.agent_state_interrupted
        AgentRunState.Expired -> R.string.agent_state_expired
        AgentRunState.Failed -> R.string.agent_state_failed
        else -> R.string.agent_state_running
    }

    /** 将紧凑 JSON 剧情状态格式化为易读多行文本，空对象返回空串以便展示空态。 */
    private fun formatSceneJson(raw: String): String {
        val parsed = runCatching { AgentJson.parse(raw).asJsonObject }.getOrNull() ?: return ""
        if (parsed.size() == 0) return ""
        return mPrettyGson.toJson(parsed)
    }

    /** 快照按核心域显示前后差异，保留自定义键及 JSON 值的实际类型。 */
    private fun sceneDifference(previous: String, current: String): String {
        val before = AgentJson.parse(previous).asJsonObject
        val after = AgentJson.parse(current).asJsonObject
        return (before.keySet() + after.keySet())
            .filter { before[it] != after[it] }
            .joinToString("\n") { key ->
                "$key: ${before[key] ?: "∅"} → ${after[key] ?: "∅"}"
            }
    }

    /** 内置工具的展示元数据，供设置页与任务卡统一引用。 */
    private data class ToolSpec(
        val id: String,
        val labelRes: Int,
        val group: AgentToolGroup,
        val writesMemory: Boolean = false,
    )

    companion object {
        private val TOOL_SPECS = listOf(
            ToolSpec("search_lore", R.string.agent_tool_search_lore, AgentToolGroup.Lore),
            ToolSpec("read_lore_entry", R.string.agent_tool_read_lore_entry, AgentToolGroup.Lore),
            ToolSpec("search_chat", R.string.agent_tool_search_chat, AgentToolGroup.Chat),
            ToolSpec("read_messages", R.string.agent_tool_read_messages, AgentToolGroup.Chat),
            ToolSpec("search_memory", R.string.agent_tool_search_memory, AgentToolGroup.Memory),
            ToolSpec("read_memory", R.string.agent_tool_read_memory, AgentToolGroup.Memory),
            ToolSpec(
                "upsert_memory",
                R.string.agent_tool_upsert_memory,
                AgentToolGroup.Memory,
                writesMemory = true,
            ),
            ToolSpec(
                "correct_memory",
                R.string.agent_tool_correct_memory,
                AgentToolGroup.Memory,
                writesMemory = true,
            ),
            ToolSpec(
                "invalidate_memory",
                R.string.agent_tool_invalidate_memory,
                AgentToolGroup.Memory,
                writesMemory = true,
            ),
            ToolSpec(
                "promote_memory_scope",
                R.string.agent_tool_promote_memory_scope,
                AgentToolGroup.Memory,
                writesMemory = true,
            ),
            ToolSpec("get_scene_state", R.string.agent_tool_get_scene_state, AgentToolGroup.Scene),
            ToolSpec(
                "update_scene_state",
                R.string.agent_tool_update_scene_state,
                AgentToolGroup.Scene
            ),
            ToolSpec("roll_dice", R.string.agent_tool_roll_dice, AgentToolGroup.Utility),
            ToolSpec("calculate", R.string.agent_tool_calculate, AgentToolGroup.Utility),
            ToolSpec("ask_user", R.string.agent_tool_ask_user, AgentToolGroup.Interaction),
        )

        val TOOL_LABELS: Map<String, Int> = TOOL_SPECS.associate { it.id to it.labelRes }
    }
}
