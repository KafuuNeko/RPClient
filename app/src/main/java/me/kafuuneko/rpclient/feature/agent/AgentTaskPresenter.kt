package me.kafuuneko.rpclient.feature.agent

import me.kafuuneko.rpclient.R
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionItem
import me.kafuuneko.rpclient.feature.agent.model.AgentQuestionKind
import me.kafuuneko.rpclient.feature.agent.model.AgentStepItem
import me.kafuuneko.rpclient.feature.agent.model.AgentStepKind
import me.kafuuneko.rpclient.feature.agent.model.AgentTaskItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallItem
import me.kafuuneko.rpclient.feature.agent.model.AgentToolCallStatus
import me.kafuuneko.rpclient.feature.agent.model.AgentWriteDisposition
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.AgentStepType
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.runtime.AgentProgressStore
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.agent.tool.catalog.ToolCatalog
import me.kafuuneko.rpclient.libs.room.entity.AgentRun
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository

/** 任务与确认卡的脱敏展示投影，不把参数和原始工具结果放入页面状态。 */
class AgentTaskPresenter(
    private val mRuns: AgentRunRepository,
    private val mProgress: AgentProgressStore,
    private val mCatalog: ToolCatalog
) {
    /** @return 对齐步骤和调用记录的任务列表，正文预览只用于活动运行。 */
    suspend fun tasks(
        conversation: ConversationRef,
        runs: List<AgentRun>,
        customToolLabels: Map<String, String>
    ): List<AgentTaskItem> {
        return runs.map { run ->
            // 按步骤归组持久化工具调用，保持执行顺序并仅展示脱敏状态。
            val invocationsByStep = mRuns.invocations(run.id).groupBy { it.stepId }
            val steps = mRuns.steps(run.id).map { step ->
                val kind = when (step.kind) {
                    AgentStepType.Tools -> AgentStepKind.Tools
                    AgentStepType.Compaction -> AgentStepKind.Compaction
                    else -> AgentStepKind.Model
                }
                val kindRes = when (kind) {
                    AgentStepKind.Tools -> R.string.agent_step_tools
                    AgentStepKind.Compaction -> R.string.agent_step_compaction
                    AgentStepKind.Model -> R.string.agent_step_model
                }
                val stepTools = invocationsByStep[step.id].orEmpty().map { invocation ->
                    toToolCallItem(invocation, customToolLabels)
                }
                AgentStepItem(
                    kindRes = kindRes,
                    completed = step.completedAt != null,
                    kind = kind,
                    tokens = step.actualTokens,
                    toolCalls = stepTools,
                )
            }
            // 仅对活动中的运行合并实时流式预览，避免已完成任务重复展示正文。
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
    }

    /** 将挂起的用户提问或确认载荷转换为结构化卡片模型。 */
    fun toQuestionItem(
        runId: String,
        result: ToolResult.AwaitingUser,
    ): AgentQuestionItem {
        val kind = when (result.kind) {
            ToolUserRequestKind.Input -> AgentQuestionKind.Input
            ToolUserRequestKind.LocalWriteConfirmation -> AgentQuestionKind.LocalWriteConfirmation
            ToolUserRequestKind.ExternalWriteConfirmation -> AgentQuestionKind.ExternalWriteConfirmation
            ToolUserRequestKind.ExternalReadConfirmation -> AgentQuestionKind.ExternalReadConfirmation
        }
        // 外部联网确认提取工具名、目标主机和脱敏参数键值，本地确认保留原摘录文本。
        val toolName = result.details["toolName"]?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(String::isNotBlank)
        val host = result.details["destinationHost"]?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf(String::isNotBlank)
        val parameters = result.details["parameters"]?.takeIf { it.isJsonObject }?.asJsonObject
            ?.entrySet()
            ?.map { (key, value) ->
                val text = if (value.isJsonPrimitive) value.asString else value.toString()
                key to text
            }
            .orEmpty()
        val details = result.details.entrySet()
            .filter { it.key !in setOf("operation", "toolName", "destinationHost", "parameters") }
            .joinToString("\n") { entry ->
                val value = entry.value
                if (value.isJsonPrimitive) value.asString else value.toString()
            }
        return AgentQuestionItem(
            runId = runId,
            question = result.question,
            options = result.options,
            confirmation = kind != AgentQuestionKind.Input,
            details = details,
            kind = kind,
            toolName = toolName,
            destinationHost = host,
            parameterLines = parameters,
        )
    }

    /** 将持久化工具调用映射为脱敏展示模型，优先呈现自定义名称与 HTTP 工具函数名。 */
    private fun toToolCallItem(
        invocation: ToolInvocation,
        customLabels: Map<String, String>,
    ): AgentToolCallItem {
        // 优先从目录或当前 HTTP 配置取展示名，已删除外部工具统一回退中性标签。
        val labelRes = mCatalog.find(invocation.toolId)?.labelRes
            ?: if (invocation.toolId.startsWith("http.") && invocation.toolId !in customLabels) {
                R.string.tool_settings_deleted_external_tool
            } else R.string.agent_step_tools
        val label = customLabels[invocation.toolId]
        // 映射调用状态与写入处置标记，不暴露任何参数或原始返回。
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
            label = label,
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

}
