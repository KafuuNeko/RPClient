package me.kafuuneko.rpclient.feature.agent

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import kotlinx.coroutines.flow.first
import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitDraft
import me.kafuuneko.rpclient.feature.agent.model.AgentLimitField
import me.kafuuneko.rpclient.feature.agent.model.AgentPresetItem
import me.kafuuneko.rpclient.feature.agent.model.AgentSceneRevisionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentSettingsDialog
import me.kafuuneko.rpclient.feature.agent.model.AgentToolItem
import me.kafuuneko.rpclient.feature.agent.model.AgentUiAction
import me.kafuuneko.rpclient.feature.agent.model.AgentUiState
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigCodec
import me.kafuuneko.rpclient.libs.agent.connection.ToolConfigurationStatus
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.runtime.AgentProgressStore
import me.kafuuneko.rpclient.libs.agent.runtime.AgentRunManager
import me.kafuuneko.rpclient.libs.agent.tool.catalog.BuiltinToolRegistrations
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolCatalog
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolGroup
import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy
import me.kafuuneko.rpclient.libs.room.entity.ToolDefinition
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.PromptPresetRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.libs.room.repository.ToolConfigRepository

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
    private val mToolConfigurations: ToolConfigRepository? = null,
    private val mToolStatus: ToolConfigurationStatus? = null,
    private val mCatalog: ToolCatalog = BuiltinToolRegistrations.catalog,
    private val mPresenter: AgentTaskPresenter = AgentTaskPresenter(mRuns, mProgress, mCatalog),
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
        // 读取内置覆写显示名与已配置 HTTP 工具名，供任务卡与设置页共用。
        val overrideDisplayNames = mToolConfigurations?.overrides().orEmpty().mapNotNull { entry ->
            val label = runCatching {
                ToolConfigCodec.parse(entry.configJson)["displayName"]?.asString
            }.getOrNull()?.takeIf(String::isNotBlank)
            label?.let { entry.toolId to it }
        }.toMap()
        val configuredTools = mToolConfigurations?.tools().orEmpty()
        val customToolLabels = overrideDisplayNames + configuredTools.associate { tool ->
            "http.${tool.id}" to tool.name
        }
        // 读取运行摘要，并将每个步骤关联到具体工具调用状态。
        val runs = mRuns.observe(conversation).first()
        val tasks = mPresenter.tasks(conversation, runs, customToolLabels)
        // 仅当存在等待态运行时读取本机挂起问题或确认摘要，并区分记忆提升与外部联网确认。
        val waiting = if (runs.any { it.state == AgentRunState.WaitingForUser }) {
            mManager().waiting(conversation)
        } else {
            null
        }
        val activeRunId = runs.firstOrNull { !it.state.isTerminal }?.id
        val question = waiting?.takeIf { activeRunId != null }?.let { result ->
            mPresenter.toQuestionItem(requireNotNull(activeRunId), result)
        }
        val base = previous.copy(
            enabled = enabled,
            tasks = tasks,
            question = question,
            selectedPresetId = preset,
        )
        if (!settings) return base
        return loadSettings(conversation, base, overrideDisplayNames, configuredTools)
    }

    /** 设置数据仅在打开设置时加载，任务刷新不反复构建剧情和预设列表。 */
    private suspend fun loadSettings(
        conversation: ConversationRef, base: AgentUiState,
        overrideDisplayNames: Map<String, String>, configuredTools: List<ToolDefinition>,
    ): AgentUiState {
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
        // 按连接一次性探测 URL 与本机凭据就绪状态，避免多个绑定工具重复访问 Keystore。
        val connectionsById = mToolConfigurations?.connections().orEmpty().associateBy { it.id }
        val connectionStates = connectionsById.mapValues { (_, connection) ->
            mToolStatus?.connectionState(connection)
        }
        val external = configuredTools.filter { tool ->
            tool.enabled || "http.${tool.id}" in selected
        }.map { tool ->
            val bound = tool.connectionId?.let(connectionsById::get)
            val readiness = bound?.let { connectionStates[it.id] }
            val missing = mToolStatus?.needsConfiguration(
                kind = tool.kind,
                connectionEnabled = bound?.enabled == true,
                connectionMissing = readiness?.needsConfiguration ?: true,
            ) ?: false
            AgentToolItem(
                id = "http.${tool.id}",
                labelRes = R.string.tool_settings_title,
                group = ToolGroup.Network,
                enabled = "http.${tool.id}" in selected,
                label = tool.name,
                needsConfiguration = missing,
                globallyDisabled = !tool.enabled,
            )
        }
        return base.copy(
            settingsLoaded = true,
            tools = mCatalog.registrations.map { registration ->
                val spec = registration.specification
                AgentToolItem(
                    id = spec.toolId,
                    labelRes = registration.labelRes,
                    group = spec.group,
                    enabled = spec.toolId in selected,
                    writesMemory = spec.writesMemory,
                    label = overrideDisplayNames[spec.toolId],
                )
            } + external,
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
    fun local(state: AgentUiState, action: AgentUiAction): AgentUiState? {
        // 仅处理纯内存态弹窗切换、草稿编辑与任务折叠，持久化动作交由 update 执行。
        return when (action) {
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

            is AgentUiAction.ChangeLimit -> changeLimit(state, action)

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
        // 取消等待或活动中的 Agent 运行，或执行独立剧情撤销流程。
        if (action == AgentUiAction.Cancel) {
            mManager().stop(conversation)
            return load(conversation, state)
        }
        if (action == AgentUiAction.UndoScene) return undoScene(conversation, state)
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
                    state.tools.map { it.id }.filter { it in dialog.selectedToolIds }
                policy = policy.copy(
                    enabledToolIdsJson = JsonArray().apply { orderedValidIds.forEach(::add) }
                        .toString(),
                )
                nextDialog = AgentSettingsDialog.None
            }

            AgentUiAction.SaveLimits -> {
                val dialog = state.settingsDialog as? AgentSettingsDialog.Limits
                val parsed = parseLimitPolicy(policy, dialog?.draft ?: state.limits)
                    ?: return invalidLimitState(state)
                policy = parsed
                nextDialog = AgentSettingsDialog.None
            }

            else -> return state
        }
        // 校验并原子保存策略，若限额越界则保留弹窗并就地提示。
        try {
            mRuns.saveSettings(conversation, enabled, presetId, policy)
        } catch (_: IllegalArgumentException) {
            return invalidLimitState(state)
        }
        mManager().stop(conversation)
        return load(
            conversation = conversation,
            previous = state.copy(settingsDialog = nextDialog, errorRes = null),
            settings = true,
        )
    }

    /** 解析限额草稿为策略数值；格式非法或毫秒换算溢出时返回空。 */
    private fun parseLimitPolicy(
        policy: ConversationToolPolicy,
        draft: AgentLimitDraft,
    ) = runCatching {
        policy.copy(
            maxToolRounds = draft.rounds.trim().toInt(),
            maxToolCalls = draft.calls.trim().toInt(),
            maxModelRequests = draft.requests.trim().toInt(),
            maxTotalTokens = draft.tokens.trim().toLong(),
            maxActiveMillis = Math.multiplyExact(draft.seconds.trim().toLong(), 1000L),
            compactionWatermarkPercent = draft.watermark.trim().toInt(),
        )
    }.getOrNull()

    /** 将限额校验错误写回当前弹窗或面板，保留用户已输入的草稿。 */
    private fun invalidLimitState(state: AgentUiState): AgentUiState {
        val dialog = state.settingsDialog as? AgentSettingsDialog.Limits
        return if (dialog != null) {
            state.copy(settingsDialog = dialog.copy(errorRes = R.string.agent_invalid_settings))
        } else {
            state.copy(errorRes = R.string.agent_invalid_settings)
        }
    }

    /** 更新限额草稿并保留当前弹窗层级，不写入权威策略。 */
    private fun changeLimit(state: AgentUiState, action: AgentUiAction.ChangeLimit): AgentUiState {
        // 优先修改打开中的限额弹窗草稿，否则回写外层预览草稿。
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
        return if (dialog != null) {
            state.copy(settingsDialog = dialog.copy(draft = updated, errorRes = null))
        } else {
            state.copy(limits = updated, errorRes = null)
        }
    }

    /** 撤销前先停止运行，再以乐观修订写入前一份剧情快照。 */
    private suspend fun undoScene(
        conversation: ConversationRef,
        state: AgentUiState
    ): AgentUiState {
        // 先中止当前会话的活动运行，避免撤销期间并发写入冲突。
        mManager().stop(conversation)
        val revisions = mScenes.revisions(conversation)
        val current = mScenes.get(conversation)
        if (revisions.isEmpty()) return state
        // 计算回退到上一修订所需的键级差异，并以当前修订号执行乐观更新。
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

    /** 新输入只取消确实存在的 Agent 占有者，普通路径无需初始化工具运行器。 */
    suspend fun stopActive(conversation: ConversationRef) {
        if (mRuns.active(conversation) != null) mManager().stop(conversation)
    }

    /** 页面离开时保留等待任务，普通页面无需创建运行器。 */
    suspend fun leaveActive(conversation: ConversationRef) {
        if (mRuns.active(conversation) != null) mManager().leave(conversation)
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

}
