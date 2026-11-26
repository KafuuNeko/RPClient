package me.kafuuneko.rpclient.libs.agent.runtime

import com.google.gson.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeout
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.tool.ToolCallRequest
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutor
import me.kafuuneko.rpclient.libs.agent.tool.ToolRegistry
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolResultCodec
import me.kafuuneko.rpclient.libs.llm.model.LLMGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.LLMToolChoice
import me.kafuuneko.rpclient.libs.llm.model.LLMToolExchange
import me.kafuuneko.rpclient.libs.llm.model.LLMToolGenerationRequest
import me.kafuuneko.rpclient.libs.llm.model.LLMToolStreamEvent
import me.kafuuneko.rpclient.libs.llm.model.LLMToolTurn
import me.kafuuneko.rpclient.libs.llm.model.LLMToolProtocolException
import me.kafuuneko.rpclient.libs.prompt.request.PromptTokenizerRegistry
import me.kafuuneko.rpclient.libs.prompt.request.PromptBudgetExceededException
import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository

/**
 * 单次角色工具循环，单聊与群聊共用且不包含工具名分支。
 * - 只执行经过协议正常终态和强参数校验的调用。
 * - 每次请求预占成本；等待用户释放执行栈，检查点保存完整有序续接。
 * - 最终正文及证据提案通过独立提交器原子写入。
 */
class AgentRuntime(
    private val mRuns: AgentRunRepository,
    private val mLLM: LLMRepository,
    private val mComposer: AgentPromptComposer,
    private val mRegistry: ToolRegistry,
    private val mExecutor: ToolExecutor,
    private val mCheckpoints: AgentCheckpointStore,
    private val mCodec: AgentCheckpointCodec,
    private val mCommitter: AgentReplyCommitter,
    private val mTokenizers: PromptTokenizerRegistry,
    private val mProgress: AgentProgressStore,
) {
    private val mPartials = ConcurrentHashMap<String, AgentPartialReply>()

    /** 停止命令只可挽救已经强制禁用工具的正文，普通工具轮次预览不能落库。 */
    internal fun partial(conversation: ConversationRef): AgentPartialReply? =
        mPartials[conversation.key]?.takeIf { it.content.isNotBlank() }
            ?.let { it.copy(content = mCommitter.clean(it.plan, it.content)) }

    /** 执行或恢复角色运行；挂起时抛出专用信号，外层不得继续调度群成员。 */
    suspend fun execute(
        token: AgentExecutionToken,
        initial: AgentRuntimeCheckpoint
    ): AgentTurnPlan {
        var checkpoint = initial
        val policy = mRuns.policy(token.conversation)
        if (checkpoint.exchanges.isEmpty() && checkpoint.pending == null) checkpoint =
            compactOnce(token, checkpoint, policy)
        mComposer.recordProjection(checkpoint.plan)
        var mustFinalize = false
        // 每轮只追加交换段，运行中不重新构建角色、世界书、预设或初始剧情。
        while (true) {
            checkpoint.pending?.let { pending ->
                val results = executeTools(token, checkpoint, pending, policy)
                checkpoint = checkpoint.copy(
                    exchanges = checkpoint.exchanges + LLMToolExchange(
                        pending,
                        results,
                        checkpoint.pendingInstruction
                    ), pending = null, pendingStepId = 0, pendingInstruction = ""
                )
                if (repeatsWithoutProgress(checkpoint)) {
                    mustFinalize = true
                    mRuns.noteOutcome(token, "repeated_reads")
                }
                save(token, checkpoint)
            }
            val plan = checkpoint.plan
            val run = requireNotNull(mRuns.get(token.runId))
            val requestBase =
                mComposer.request(plan).copy(exchanges = checkpoint.exchanges, runId = token.runId)
            val inputTokens = mComposer.inputTokens(plan, requestBase)
            requireInputBudget(plan, inputTokens)
            val batch = mRuns.batchUsage(token)
            val groupNearLimit = token.conversation is ConversationRef.Group &&
                (batch.first >= policy.maxGroupModelRequests - 1 || batch.second + 2 * (inputTokens + plan.input.provider.maxTokens) >= policy.maxGroupTotalTokens)
            val nearingLimit =
                groupNearLimit || mustFinalize || run.toolRounds >= policy.maxToolRounds || run.toolCalls >= policy.maxToolCalls || run.modelRequests >= policy.maxModelRequests - 1 ||
                    run.totalTokens + 2 * (inputTokens + plan.input.provider.maxTokens) >= policy.maxTotalTokens
            val guidance =
                if (nearingLimit) plan.finalizeInstruction else if (checkpoint.exchanges.lastOrNull()?.results?.any { it["kind"]?.asString != "success" } == true) plan.errorInstruction else ""
            val request = requestBase.copy(
                choice = if (nearingLimit) LLMToolChoice.None else LLMToolChoice.Auto,
                tailInstruction = guidance
            )
            val projectedInput = mComposer.inputTokens(plan, request)
            requireInputBudget(plan, projectedInput)
            mComposer.recordProjection(plan, request)
            val turn = requestModel(token, plan, request, projectedInput, policy)
            if (turn.calls.isEmpty()) {
                mRuns.transition(token, AgentRunState.Committing)
                val committed = mCommitter.commit(token, plan, turn.content)
                mRuns.finish(committed, AgentRunState.Completed)
                mCheckpoints.delete("run/${token.runId}")
                mRuns.invocations(token.runId).mapNotNull { it.checkpointKey }
                    .forEach { mCheckpoints.delete(it) }
                return plan
            }
            if (nearingLimit) throw AgentRuntimeException("tools_returned_after_finalize")
            val step = mRuns.beginTools(token)
            checkpoint =
                checkpoint.copy(pending = turn, pendingStepId = step, pendingInstruction = guidance)
            save(token, checkpoint)
        }
    }

    /** 仅由撤权事务使用已冻结并清洗的停止快照，不提交任何未决工具提案。 */
    internal suspend fun persistPartial(partial: AgentPartialReply): Long? =
        mCommitter.persistPartial(partial.plan, partial.content)

    /** 停止全部运行并替换数据库后清理本机临时状态。 */
    fun clear() {
        mPartials.clear()
        mProgress.clear()
    }

    /** 撤权或异常收尾后释放内存预览引用。 */
    fun release(conversation: ConversationRef) {
        mPartials.remove(conversation.key)
    }

    /** 检查点在轮次边界发布，签名和原始结果不进入 Room 或 UiState。 */
    suspend fun save(token: AgentExecutionToken, checkpoint: AgentRuntimeCheckpoint) {
        val key = "run/${token.runId}"
        val digest = mCheckpoints.write(key, mCodec.encode(checkpoint))
        mRuns.checkpoint(token, key, digest)
    }

    private suspend fun requestModel(
        token: AgentExecutionToken,
        plan: AgentTurnPlan,
        request: LLMToolGenerationRequest,
        inputTokens: Long,
        policy: ConversationToolPolicy
    ): LLMToolTurn {
        mRuns.transition(token, AgentRunState.RequestingModel)
        val step = mRuns.reserveRequest(
            token,
            UUID.randomUUID().toString(),
            inputTokens + plan.input.provider.maxTokens,
            false
        )
        val start = System.nanoTime()
        mProgress.begin(token.conversation, token.runId)
        if (request.choice == LLMToolChoice.None) mPartials[token.conversation.key] =
            AgentPartialReply(token, plan, "")
        val turn = withTimeout(activeBudget(token, policy)) {
            if (!plan.input.streaming) mLLM.generateToolTurn(plan.input.provider, request)
            else {
                var finished: LLMToolTurn? = null
                mLLM.streamToolTurn(plan.input.provider, request).collect { event ->
                    when (event) {
                        is LLMToolStreamEvent.Finished -> finished = event.turn
                        is LLMToolStreamEvent.Preview -> {
                            mProgress.append(
                                token.conversation,
                                token.runId,
                                event.text,
                                event.reasoning
                            )
                            if (!event.reasoning && request.choice == LLMToolChoice.None) mPartials.computeIfPresent(
                                token.conversation.key
                            ) { _, partial -> partial.copy(content = partial.content + event.text) }
                        }

                        LLMToolStreamEvent.Connected -> Unit
                    }
                }
                finished ?: throw AgentRuntimeException("stream_interrupted")
            }
        }
        mPartials.remove(token.conversation.key)
        mProgress.complete(token.conversation, token.runId, turn.content, turn.reasoning)
        val usage = turn.usage.takeIf { plan.input.provider.useServerReportedUsage }
        val estimatedOutput =
            mTokenizers.resolve(plan.input.provider).countText(turn.continuation.toString())
                .toLong()
        mRuns.completeRequest(
            token,
            step,
            (usage?.promptTokens?.toLong() ?: inputTokens) + (usage?.completionTokens?.toLong()
                ?: estimatedOutput),
            (System.nanoTime() - start) / 1_000_000
        )
        return turn
    }

    private suspend fun executeTools(
        token: AgentExecutionToken,
        checkpoint: AgentRuntimeCheckpoint,
        pending: LLMToolTurn,
        policy: ConversationToolPolicy
    ): List<JsonObject> {
        mRuns.transition(token, AgentRunState.ExecutingTools)
        val definitions = mComposer.request(checkpoint.plan).tools.associateBy { it.name }
        val calls = pending.calls.mapIndexed { index, call ->
            val definition = definitions[call.name]
            val registered = definition?.let { mRegistry.find(it.toolId) }
            ToolCallRequest(
                checkpoint.pendingStepId,
                index,
                definition?.toolId ?: "unknown/${call.name}",
                definition?.contractVersion ?: 1,
                registered?.digest.orEmpty(),
                call.arguments,
                call.remoteId,
                independent = registered?.definition?.let { it.effect == ToolEffect.ReadOnly && it.parallelSafe } == true)
        }
        val start = System.nanoTime()
        val remaining = activeBudget(token, policy)
        val results =
            withTimeout(remaining) { mExecutor.executeBatch(token, calls) }
        mRuns.addActiveTime(token, (System.nanoTime() - start) / 1_000_000)
        if (results.any { it is ToolResult.FatalError }) throw AgentRuntimeException("tool_execution_failed")
        if (results.any { it is ToolResult.AwaitingUser }) {
            save(token, checkpoint)
            throw AgentWaitingForUser()
        }
        mRuns.completeTools(token, checkpoint.pendingStepId)
        return results.map(ToolResultCodec::encode)
    }

    private suspend fun compactOnce(
        token: AgentExecutionToken,
        initial: AgentRuntimeCheckpoint,
        policy: ConversationToolPolicy
    ): AgentRuntimeCheckpoint {
        val plan = initial.plan
        val originalInputTokens = mComposer.inputTokens(plan, mComposer.request(plan))
        if (originalInputTokens * 100 < (plan.input.provider.contextTokens - plan.input.provider.maxTokens).toLong() * policy.compactionWatermarkPercent) return initial
        val candidates = mComposer.compactionCandidates(plan.input)
        if (candidates.isEmpty()) return initial
        val provider = plan.input.provider
        val history = candidates.joinToString("\n\n") { "${it.role}: ${it.content}" }
        val messages = listOfNotNull(
            provider.systemPrompt.takeIf(String::isNotBlank)
                ?.let { LLMMessage(LLMMessageRole.System, it) },
            LLMMessage(LLMMessageRole.System, plan.compactionInstruction),
            LLMMessage(LLMMessageRole.User, history)
        )
        val tokenizer = mTokenizers.resolve(provider)
        val request = LLMToolGenerationRequest(
            base = LLMGenerationRequest(
                messages,
                model = provider.model,
                routingSessionId = plan.input.request.routingSessionId,
                isPromptFinalized = true
            ),
            tools = emptyList(),
            choice = LLMToolChoice.None,
            systemPromptApplied = true,
            runId = token.runId,
        )
        val inputTokens =
            mComposer.inputTokens(plan.copy(input = plan.input.copy(streaming = false)), request)
        if (inputTokens > provider.contextTokens - provider.maxTokens) return initial
        val estimate = inputTokens + provider.maxTokens
        // 压缩也使用同源投影和运行关联账本，空工具集不发送工具字段。
        val step = mRuns.reserveRequest(token, UUID.randomUUID().toString(), estimate, true)
        val start = System.nanoTime()
        val response = try {
            withTimeout(activeBudget(token, policy)) { mLLM.generateToolTurn(provider, request) }
        } catch (error: LLMToolProtocolException) {
            if (error.code != "incomplete_or_rejected") throw error
            // 额度截断的摘要不替换历史，保守保留已消耗估算且只尝试一次。
            mRuns.completeRequest(
                token,
                step,
                estimate,
                (System.nanoTime() - start) / 1_000_000,
                "discarded_incomplete"
            )
            save(token, initial)
            return initial
        }
        if (response.calls.isNotEmpty()) throw AgentRuntimeException("tools_returned_after_finalize")
        val usage = response.usage.takeIf { provider.useServerReportedUsage }
        val actual =
            (usage?.promptTokens?.toLong() ?: inputTokens) + (usage?.completionTokens?.toLong()
                ?: tokenizer.countText(response.continuation.toString()).toLong())
        mRuns.completeRequest(token, step, actual, (System.nanoTime() - start) / 1_000_000)
        val compacted = mComposer.compact(plan, candidates, response.content)
        val checkpoint = initial.copy(plan = compacted)
        save(token, checkpoint)
        return checkpoint
    }

    /** 复用普通 Prompt 的结构化预算错误，页面可显示已本地化的具体 Token 缺口。 */
    private fun requireInputBudget(plan: AgentTurnPlan, tokens: Long) {
        val budget = plan.input.provider.contextTokens - plan.input.provider.maxTokens
        if (tokens > budget) throw PromptBudgetExceededException(
            tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), budget
        )
    }

    /** 单成员与群聊批次同时约束当前请求，等待用户不进入主动耗时。 */
    private suspend fun activeBudget(
        token: AgentExecutionToken,
        policy: ConversationToolPolicy
    ): Long {
        val run = requireNotNull(mRuns.get(token.runId))
        val own = policy.maxActiveMillis - run.activeMillis
        val batch =
            if (token.conversation is ConversationRef.Group && run.batchId != null) policy.maxGroupActiveMillis - mRuns.batchUsage(
                token
            ).third else Long.MAX_VALUE
        return minOf(own, batch).takeIf { it > 0 }
            ?: throw AgentRuntimeException("active_budget_exceeded")
    }

    /** 确定性读取连续三轮完全相同时没有进展；随机工具永远不触发此拦截。 */
    private fun repeatsWithoutProgress(checkpoint: AgentRuntimeCheckpoint): Boolean {
        val last = checkpoint.exchanges.takeLast(3)
        if (last.size != 3) return false
        if (last.any { exchange ->
                exchange.turn.calls.any { call ->
                    mRegistry.available(checkpoint.plan.toolDigests.keys)
                        .firstOrNull { it.definition.name == call.name }?.definition?.effect != ToolEffect.ReadOnly
                }
            }) return false
        return last.map { exchange ->
            AgentJson.digest(exchange.turn.calls.joinToString { it.name + it.arguments } + exchange.results.joinToString {
                AgentJson.canonical(
                    it
                )
            })
        }.distinct().size == 1
    }
}
