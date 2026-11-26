package me.kafuuneko.rpclient.libs.room.repository

import androidx.room.withTransaction
import java.util.UUID
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.AgentPolicyValidator
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.AgentRun
import me.kafuuneko.rpclient.libs.room.entity.AgentStep
import me.kafuuneko.rpclient.libs.room.entity.ConversationToolPolicy
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation
import me.kafuuneko.rpclient.libs.room.entity.UserPersona

/** 运行或策略已变化时拒绝提交，异常不包含会话正文和工具参数。 */
class AgentAuthorizationException : IllegalStateException("Agent execution is no longer authorized")

/** 调用是否首次取得执行权，已有调用只能回放结果或报告未决。 */
data class ToolInvocationClaim(val invocation: ToolInvocation, val isNew: Boolean)

/**
 * Agent 权威元数据和短事务提交入口。
 * - 会话独占、设置版本、历史时钟和执行世代共同决定写入资格。
 * - 先持久化派发标记再执行，恢复时不自动重发未决副作用。
 * - 不在事务内发网络请求、等待用户或读写检查点文件。
 */
class AgentRunRepository(private val mDatabase: AppDatabase) {
    private val mDao = mDatabase.getAgentRunDao()

    /** 观察指定身份域的运行摘要。 */
    fun observe(conversation: ConversationRef) = mDao.observeRuns(conversation.key)

    /** 获取或创建默认工具策略，不会开启 Agent 或影响普通生成入口。 */
    suspend fun policy(conversation: ConversationRef): ConversationToolPolicy =
        mDatabase.withTransaction {
            settings(conversation)
            mDao.policy(conversation.key) ?: ConversationToolPolicy(
                conversationKey = conversation.key,
                chatId = (conversation as? ConversationRef.Single)?.id,
                groupId = (conversation as? ConversationRef.Group)?.id,
            ).also { mDao.insertPolicy(it) }
        }

    /**
     * 保存会话开关、预设及工具策略，并在同一事务先撤销当前运行资格。
     * @param policy 新策略；为空时只修改开关与预设。提交时钟由仓库保留。
     */
    suspend fun saveSettings(
        conversation: ConversationRef,
        enabled: Boolean,
        presetId: Long?,
        policy: ConversationToolPolicy? = null,
    ) = mDatabase.withTransaction {
        val current = this@AgentRunRepository.policy(conversation)
        val next = (policy ?: current).copy(
            historyVersion = current.historyVersion,
            executionEpoch = current.executionEpoch + 1
        )
        require(next.conversationKey == current.conversationKey && next.chatId == current.chatId && next.groupId == current.groupId) { "Cannot change policy owner" }
        AgentPolicyValidator.validate(next)
        // 设置更新与撤权原子提交，页面的旧表单不能恢复旧运行。
        when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatSessionDao()
                .updateAgentSettings(conversation.id, enabled, presetId)

            is ConversationRef.Group -> mDatabase.getGroupChatSessionDao()
                .updateAgentSettings(conversation.id, enabled, presetId)
        }
        mDao.updatePolicy(next)
        revokeActive(conversation, AgentRunState.Cancelled)
    }

    /** 创建独占运行并冻结当前提交时钟；角色与实际人设必须属于当前会话。 */
    suspend fun start(
        conversation: ConversationRef,
        characterId: Long,
        personaId: Long,
        batchId: String? = null,
    ): AgentExecutionToken = mDatabase.withTransaction {
        val settings = settings(conversation)
        if (!settings.enabled || mDao.activeRun(conversation.key) != null) throw AgentAuthorizationException()
        validateIdentity(conversation, settings.personaId, characterId, personaId)
        val policy = policy(conversation)
        AgentPolicyValidator.validate(policy)
        // 世代随每次新运行递增，旧回调即使持有同一设置版本也无法获得资格。
        val epoch = policy.executionEpoch + 1
        mDao.updatePolicy(policy.copy(executionEpoch = epoch))
        val now = System.currentTimeMillis()
        val run = AgentRun(
            id = UUID.randomUUID().toString(),
            batchId = batchId,
            conversationKey = conversation.key,
            activeConversationKey = conversation.key,
            characterId = characterId,
            personaId = personaId,
            settingsVersion = settings.version,
            historyVersion = policy.historyVersion,
            executionEpoch = epoch,
            createdAt = now,
            updatedAt = now,
        )
        mDao.insertRun(run)
        AgentExecutionToken(conversation, run.id, settings.version, policy.historyVersion, epoch)
    }

    /** 分配低频步骤，协议 requestId 的唯一约束防止重复账本条目。 */
    suspend fun appendStep(
        token: AgentExecutionToken,
        kind: String,
        requestId: String? = null
    ): Long = mDatabase.withTransaction {
        // 序号与步骤写入共享事务，防止并发请求得到相同位置。
        checkAuthorized(token)
        require(kind in setOf("Model", "Tools", "Compaction")) { "Invalid agent step kind" }
        mDao.insertStep(
            AgentStep(
                runId = token.runId,
                ordinal = mDao.nextStepOrdinal(token.runId),
                kind = kind,
                requestId = requestId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** 校验当前运行和工具授权；对回放同样检查停用与版本收紧。 */
    suspend fun authorizeTool(token: AgentExecutionToken, definition: AgentToolDefinition) =
        mDatabase.withTransaction {
            val policy = checkAuthorized(token).second
            if (definition.toolId !in enabledTools(policy) || (definition.writesMemory && policy.memoryReadOnly)) throw AgentAuthorizationException()
        }

    /** 从持久化运行注入工具身份，模型参数不能指定其他角色或人设。 */
    suspend fun toolContext(
        token: AgentExecutionToken,
        stepId: Long,
        invocationId: String
    ): ToolExecutionContext = mDatabase.withTransaction {
        // 身份从运行记录读取，禁止调用方或模型另传可见范围。
        val run = checkAuthorized(token).first
        require(mDao.step(stepId)?.runId == token.runId) { "Invalid tool step owner" }
        ToolExecutionContext(
            token,
            stepId,
            invocationId,
            requireNotNull(run.characterId),
            requireNotNull(run.personaId)
        )
    }

    /** 副作用之前持久化派发标记，同一 Prepared 调用仅允许一个执行者取得资格。 */
    suspend fun dispatchInvocation(
        token: AgentExecutionToken,
        invocationId: String,
        definition: AgentToolDefinition
    ) = mDatabase.withTransaction {
        authorizeTool(token, definition)
        val invocation = requireNotNull(mDao.invocation(invocationId))
        check(invocation.runId == token.runId && invocation.state == ToolInvocationState.Prepared) { "Tool invocation is not prepared" }
        check(mDao.run(token.runId)?.state != AgentRunState.WaitingForUser) { "Agent run is awaiting user" }
        mDao.updateInvocation(invocation.copy(state = ToolInvocationState.Dispatching))
    }

    /** 用户明确确认后重新准备外部写入；此入口只能由用户操作调用。 */
    suspend fun confirmInvocation(token: AgentExecutionToken, invocationId: String) =
        mDatabase.withTransaction {
            val run = checkAuthorized(token).first
            val invocation = requireNotNull(mDao.invocation(invocationId))
            check(
                invocation.runId == run.id && invocation.state == ToolInvocationState.AwaitingUser &&
                    invocation.awaitingKind in setOf(
                    ToolUserRequestKind.ExternalWriteConfirmation.name,
                    ToolUserRequestKind.LocalWriteConfirmation.name
                )
            ) { "Tool invocation is not awaiting confirmation" }
            mDao.updateInvocation(
                invocation.copy(
                    state = ToolInvocationState.Prepared,
                    confirmationGranted = true,
                    checkpointKey = null,
                    resultDigest = null
                )
            )
            mDao.updateRun(
                run.copy(
                    state = AgentRunState.ExecutingTools,
                    expiresAt = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    /**
     * 原子取得客户端调用的执行权，返回已有记录时绝不再次执行处理器。
     * @param invocation 已绑定定义、参数指纹与客户端序号的调用记录。
     */
    suspend fun claimInvocation(
        token: AgentExecutionToken,
        invocation: ToolInvocation,
        definition: AgentToolDefinition
    ): ToolInvocationClaim = mDatabase.withTransaction {
        val (run, policy) = checkAuthorized(token)
        authorizeTool(token, definition)
        require(
            invocation.ordinal >= 0 && invocation.id == AgentJson.digest("${token.runId}/${invocation.stepId}/${invocation.ordinal}") &&
                invocation.toolId == definition.toolId && invocation.contractVersion == definition.contractVersion &&
                invocation.state == ToolInvocationState.Prepared
        ) { "Invalid tool invocation identity" }
        require(invocation.runId == token.runId && mDao.step(invocation.stepId)?.runId == token.runId) { "Invalid tool invocation owner" }
        val previous = mDao.invocation(invocation.id)
        if (previous != null) {
            require(
                previous.runId == invocation.runId && previous.stepId == invocation.stepId && previous.ordinal == invocation.ordinal &&
                    previous.toolId == invocation.toolId && previous.contractVersion == invocation.contractVersion &&
                    previous.definitionDigest == invocation.definitionDigest && previous.argumentsDigest == invocation.argumentsDigest
            ) { "Tool invocation identity mismatch" }
            return@withTransaction ToolInvocationClaim(previous, false)
        }
        check(run.state != AgentRunState.WaitingForUser) { "Agent run is awaiting user" }
        // 已有调用的回放不额外计数；全新调用在派发前消耗额度。
        check(run.toolCalls < policy.maxToolCalls) { "Tool invocation limit exceeded" }
        mDao.insertInvocation(invocation)
        mDao.updateRun(
            run.copy(
                toolCalls = run.toolCalls + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
        ToolInvocationClaim(invocation, true)
    }

    /**
     * 保存结果引用并原子执行本地变更；文件载荷必须由调用方在事务外预先发布。
     * @param commit 仅执行短时本地数据库操作，禁止网络、文件 IO、等待和长计算。
     */
    suspend fun completeInvocation(
        token: AgentExecutionToken,
        invocationId: String,
        definition: AgentToolDefinition,
        resultDigest: String,
        checkpointKey: String,
        state: ToolInvocationState,
        writeDisposition: String?,
        awaitingKind: String? = null,
        commit: suspend () -> Unit = {},
    ) = mDatabase.withTransaction {
        authorizeTool(token, definition)
        val invocation = requireNotNull(mDao.invocation(invocationId))
        check(
            invocation.runId == token.runId && (invocation.state == ToolInvocationState.Dispatching ||
                (state == ToolInvocationState.AwaitingUser && invocation.state == ToolInvocationState.Prepared))
        ) { "Tool invocation is not dispatching" }
        require(
            state in setOf(
                ToolInvocationState.Completed,
                ToolInvocationState.Failed,
                ToolInvocationState.AwaitingUser
            )
        ) { "Invalid tool completion state" }
        // 处理器写入与完成标记同一事务，取消或冲突时整批回滚。
        commit()
        mDao.updateInvocation(
            invocation.copy(
                state = state,
                resultDigest = resultDigest,
                checkpointKey = checkpointKey,
                writeDisposition = writeDisposition,
                awaitingKind = awaitingKind,
                completedAt = System.currentTimeMillis()
            )
        )
        if (state == ToolInvocationState.AwaitingUser) {
            val run = requireNotNull(mDao.run(token.runId))
            val now = System.currentTimeMillis()
            mDao.updateRun(
                run.copy(
                    state = AgentRunState.WaitingForUser,
                    expiresAt = now + 86_400_000,
                    updatedAt = now
                )
            )
        }
    }

    /** 将未决派发标记为结果未知；不确定时宁可终止，也不重复掷骰或写入。 */
    suspend fun markUnknown(invocationId: String) = mDatabase.withTransaction {
        val invocation = mDao.invocation(invocationId) ?: return@withTransaction
        if (invocation.state == ToolInvocationState.Dispatching) mDao.updateInvocation(
            invocation.copy(
                state = ToolInvocationState.UnknownOutcome
            )
        )
    }

    /**
     * 在同一短事务执行消息/状态提交并推进运行自身的历史时钟。
     * @param commit 已清洗、已计量的数据库变更，禁止网络及文件 IO。
     * @return 提交后的新令牌，群聊下一成员必须使用此令牌。
     */
    suspend fun commitAuthorized(
        token: AgentExecutionToken,
        commit: suspend () -> Unit
    ): AgentExecutionToken = mDatabase.withTransaction {
        val run = checkAuthorized(token).first
        // 本次提交自身触发的历史时钟变更要回写给后续角色运行。
        commit()
        val historyVersion = requireNotNull(mDao.policy(token.conversation.key)).historyVersion
        mDao.updateRun(
            requireNotNull(mDao.run(token.runId)).copy(
                historyVersion = historyVersion,
                updatedAt = System.currentTimeMillis()
            )
        )
        token.copy(historyVersion = historyVersion)
    }

    /** 停止先递增世代并释放归属，迟到回调不能提交；Job 取消由运行管理器负责。 */
    suspend fun revoke(conversation: ConversationRef) = mDatabase.withTransaction {
        val policy = policy(conversation)
        mDao.updatePolicy(policy.copy(executionEpoch = policy.executionEpoch + 1))
        revokeActive(conversation, AgentRunState.Cancelled)
    }

    /**
     * 先独立提交撤权，再在另一短事务中挽救合法 partial，挽救失败不能恢复旧授权。
     * @param partialToken 只来自已明确禁工具的收尾请求，其他阶段为空。
     * @param persistPartial 已完成清洗的本地正文变更，禁止 IO 或提案提交。
     */
    suspend fun revokeWithPartial(
        conversation: ConversationRef,
        partialToken: AgentExecutionToken?,
        persistPartial: suspend () -> Long?
    ) {
        val proof = mDatabase.withTransaction {
            val run = mDao.activeRun(conversation.key) ?: return@withTransaction null
            val authorized = partialToken?.takeIf { it.runId == run.id }
                ?.let { token -> runCatching { checkAuthorized(token) }.isSuccess } == true
            val policy = policy(conversation)
            mDao.updatePolicy(policy.copy(executionEpoch = policy.executionEpoch + 1))
            interrupt(run, AgentRunState.Cancelled)
            if (authorized) run to (policy.executionEpoch + 1) else null
        } ?: return
        // 挽救失败不能回滚已经提交的撤权；新消息或新运行出现时放弃旧片段。
        runCatching {
            mDatabase.withTransaction {
                val (run, epoch) = proof
                val policy = mDao.policy(conversation.key) ?: return@withTransaction
                if (mDao.activeRun(conversation.key) != null || policy.executionEpoch != epoch || policy.historyVersion != run.historyVersion || settings(
                        conversation
                    ).version != run.settingsVersion
                ) return@withTransaction
                persistPartial()?.let { messageId ->
                    mDao.updateRun(
                        requireNotNull(mDao.run(run.id)).copy(
                            messageId = messageId
                        )
                    )
                }
            }
        }
    }

    /** 全库替换前撤销全部资格，不依赖页面存活。 */
    suspend fun revokeAll() = mDatabase.withTransaction {
        mDao.activeRuns().forEach { run ->
            val policy = requireNotNull(mDao.policy(run.conversationKey))
            mDao.updatePolicy(policy.copy(executionEpoch = policy.executionEpoch + 1))
            interrupt(run, AgentRunState.Cancelled)
        }
    }

    /** 冷启动回收非等待运行，等待超过一天同样过期；调用者应在后台协程执行。 */
    suspend fun recoverInterrupted(now: Long = System.currentTimeMillis()) =
        mDatabase.withTransaction {
            // 等待用户不占执行协程；其他非终态视为进程异常中断。
            mDao.activeRuns().forEach { run ->
                if (run.state != AgentRunState.WaitingForUser) interrupt(
                    run,
                    AgentRunState.Interrupted
                )
                else if (run.expiresAt == null || run.expiresAt <= now) interrupt(
                    run,
                    AgentRunState.Expired
                )
            }
        }

    /** 完成或失败收尾并释放会话归属。 */
    suspend fun finish(token: AgentExecutionToken, state: AgentRunState) =
        mDatabase.withTransaction {
            require(state.isTerminal) { "Expected terminal agent state" }
            val run = checkAuthorized(token).first
            interrupt(run, state)
        }

    /** 仅撤销仍归属于旧运行的执行权；历史冲突后也不能遗留锁死的占有者。 */
    suspend fun abortOwned(token: AgentExecutionToken, state: AgentRunState) =
        mDatabase.withTransaction {
            val run = mDao.run(token.runId) ?: return@withTransaction
            if (run.activeConversationKey != token.conversation.key) return@withTransaction
            interrupt(run, state)
        }

    /** 等待超时独立于冷启动回收，不能误中止同进程仍在执行的网络请求。 */
    suspend fun expireWaiting(now: Long = System.currentTimeMillis()) = mDatabase.withTransaction {
        mDao.activeRuns()
            .filter { it.state == AgentRunState.WaitingForUser && (it.expiresAt ?: 0) <= now }
            .forEach { interrupt(it, AgentRunState.Expired) }
    }

    /** 后台初始化收集仍可保留的本机载荷键，不包含已完成运行。 */
    suspend fun recoveryKeys(now: Long = System.currentTimeMillis()): Set<String> =
        mDao.recoverableRuns(now, now - 604_800_000L).flatMap { run ->
            listOfNotNull(run.checkpointKey) + mDao.invocations(run.id)
                .mapNotNull { it.checkpointKey }
        }.toSet()

    /** 按实际人设查找活动会话，包含绑定 FOLLOW_DEFAULT 时解析得到的身份。 */
    suspend fun activeForPersona(personaId: Long): List<ConversationRef> =
        mDao.activeRuns().filter { it.personaId == personaId }.map { run ->
            val id = run.conversationKey.substringAfter('/').toLong()
            if (run.conversationKey.startsWith("single/")) ConversationRef.Single(id) else ConversationRef.Group(
                id
            )
        }

    /** 获取运行摘要，恢复和任务卡不依赖调试日志。 */
    suspend fun get(runId: String): AgentRun? = mDao.run(runId)

    /** 查询当前会话占有者，包括持久化等待状态。 */
    suspend fun active(conversation: ConversationRef): AgentRun? = mDao.activeRun(conversation.key)

    /** 获取当前运行的步骤摘要。 */
    suspend fun steps(runId: String): List<AgentStep> = mDao.steps(runId)

    /** 获取用于恢复的有序调用索引，不返回原始参数。 */
    suspend fun invocations(runId: String): List<ToolInvocation> = mDao.invocations(runId)

    /** 复核资格后更新低频阶段。 */
    suspend fun transition(token: AgentExecutionToken, state: AgentRunState) =
        mDatabase.withTransaction {
            val run = checkAuthorized(token).first
            require(!state.isTerminal && state != AgentRunState.WaitingForUser) { "Invalid execution transition" }
            mDao.updateRun(
                run.copy(
                    state = state,
                    updatedAt = System.currentTimeMillis(),
                    expiresAt = null
                )
            )
        }

    /** 原子保存运行恢复引用，载荷及指纹必须已在事务外发布。 */
    suspend fun checkpoint(token: AgentExecutionToken, key: String, digest: String) =
        mDatabase.withTransaction {
            val run = checkAuthorized(token).first
            mDao.updateRun(
                run.copy(
                    checkpointKey = key,
                    checkpointDigest = digest,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    /** 预占唯一请求及估算额度，失败或取消不退还已可能消耗的成本。 */
    suspend fun reserveRequest(
        token: AgentExecutionToken,
        requestId: String,
        estimate: Long,
        compaction: Boolean
    ): Long = mDatabase.withTransaction {
        val (run, policy) = checkAuthorized(token)
        check(run.modelRequests < policy.maxModelRequests && run.totalTokens + estimate <= policy.maxTotalTokens && run.activeMillis < policy.maxActiveMillis) { "Agent cost limit reached" }
        if (token.conversation is ConversationRef.Group && run.batchId != null) {
            val batch = mDao.batchRuns(run.batchId, token.conversation.key)
            check(batch.sumOf { it.modelRequests } < policy.maxGroupModelRequests && batch.sumOf { it.totalTokens } + estimate <= policy.maxGroupTotalTokens && batch.sumOf { it.activeMillis } < policy.maxGroupActiveMillis) { "Agent group cost limit reached" }
        }
        val step = appendStep(token, if (compaction) "Compaction" else "Model", requestId)
        mDao.updateStep(requireNotNull(mDao.step(step)).copy(estimatedTokens = estimate))
        mDao.updateRun(
            run.copy(
                modelRequests = run.modelRequests + 1,
                totalTokens = run.totalTokens + estimate
            )
        )
        step
    }

    /** 成功请求按实际或同源估算核销一次，重复完成不能重复记账。 */
    suspend fun completeRequest(
        token: AgentExecutionToken,
        stepId: Long,
        tokens: Long,
        activeMillis: Long,
        outcomeCode: String = "completed",
    ) = mDatabase.withTransaction {
        require(tokens >= 0 && activeMillis >= 0) { "Invalid request accounting" }
        val run = checkAuthorized(token).first
        val step = requireNotNull(mDao.step(stepId))
        require(step.runId == token.runId) { "Invalid request step owner" }
        if (step.completedAt != null) return@withTransaction
        mDao.updateStep(
            step.copy(
                completedAt = System.currentTimeMillis(),
                actualTokens = tokens,
                outcomeCode = outcomeCode
            )
        )
        mDao.updateRun(
            run.copy(
                totalTokens = (run.totalTokens - step.estimatedTokens + tokens).coerceAtLeast(
                    0
                ), activeMillis = run.activeMillis + activeMillis
            )
        )
    }

    /** 群聊成员共用同一批次账本，收尾请求也需为其他成员已耗额度留出空间。 */
    suspend fun batchUsage(token: AgentExecutionToken): Triple<Int, Long, Long> {
        val run = requireNotNull(mDao.run(token.runId))
        val batch = run.batchId?.let { mDao.batchRuns(it, token.conversation.key) }.orEmpty()
        return Triple(
            batch.sumOf { it.modelRequests },
            batch.sumOf { it.totalTokens },
            batch.sumOf { it.activeMillis })
    }

    /** 工具轮次也计入根运行限额，不因用户等待和恢复而重置。 */
    suspend fun beginTools(token: AgentExecutionToken): Long = mDatabase.withTransaction {
        val (run, policy) = checkAuthorized(token)
        check(run.toolRounds < policy.maxToolRounds && run.toolCalls < policy.maxToolCalls) { "Agent tool limit reached" }
        mDao.updateRun(
            run.copy(
                state = AgentRunState.ExecutingTools,
                toolRounds = run.toolRounds + 1
            )
        )
        appendStep(token, "Tools")
    }

    /** 整批工具均返回结果后完成步骤，用户等待与恢复不会重复创建或提前结束步骤。 */
    suspend fun completeTools(token: AgentExecutionToken, stepId: Long) =
        mDatabase.withTransaction {
            checkAuthorized(token)
            val step = requireNotNull(mDao.step(stepId))
            require(step.runId == token.runId && step.kind == "Tools") { "Invalid tool step owner" }
            if (step.completedAt == null) mDao.updateStep(
                step.copy(
                    completedAt = System.currentTimeMillis(),
                    outcomeCode = "completed"
                )
            )
        }

    /** 保存可展示的稳定终止原因，避免把协议载荷或异常原文写入任务卡。 */
    suspend fun noteOutcome(token: AgentExecutionToken, code: String) = mDatabase.withTransaction {
        val run = checkAuthorized(token).first
        mDao.updateRun(run.copy(outcomeCode = code))
    }

    /** 工具耗时计入主动执行时间，等待用户不计入该账本。 */
    suspend fun addActiveTime(token: AgentExecutionToken, elapsed: Long) =
        mDatabase.withTransaction {
            val run = checkAuthorized(token).first
            mDao.updateRun(run.copy(activeMillis = run.activeMillis + elapsed.coerceAtLeast(0)))
        }

    /** 最终正文提交事务内关联气泡，失败诊断只保存稳定错误码。 */
    suspend fun attachMessage(token: AgentExecutionToken, messageId: Long) {
        val run = requireNotNull(mDao.run(token.runId))
        mDao.updateRun(run.copy(messageId = messageId))
    }

    /** 本机等待恢复重建令牌后仍须通过完整授权校验。 */
    suspend fun resumeToken(conversation: ConversationRef): AgentExecutionToken =
        mDatabase.withTransaction {
            val run = mDao.activeRun(conversation.key) ?: throw AgentAuthorizationException()
            val token = AgentExecutionToken(
                conversation,
                run.id,
                run.settingsVersion,
                run.historyVersion,
                run.executionEpoch
            )
            checkAuthorized(token)
            token
        }

    /** 用户输入完成后更新调用结果引用，提问答案不能授予写入确认。 */
    suspend fun answerInput(
        token: AgentExecutionToken,
        invocationId: String,
        key: String,
        digest: String
    ) = mDatabase.withTransaction {
        val run = checkAuthorized(token).first
        val invocation = requireNotNull(mDao.invocation(invocationId))
        check(invocation.runId == token.runId && invocation.state == ToolInvocationState.AwaitingUser && invocation.awaitingKind == ToolUserRequestKind.Input.name) { "Invocation is not awaiting input" }
        mDao.updateInvocation(
            invocation.copy(
                state = ToolInvocationState.Completed,
                checkpointKey = key,
                resultDigest = digest,
                completedAt = System.currentTimeMillis()
            )
        )
        mDao.updateRun(run.copy(state = AgentRunState.ExecutingTools, expiresAt = null))
    }

    /** 读取并验证工具 ID 白名单，不接受逗号串或隐式类型转换。 */
    fun enabledTools(policy: ConversationToolPolicy): Set<String> =
        AgentPolicyValidator.enabledTools(policy.enabledToolIdsJson)

    /** 每次提交重新读取全部时钟，界面关闭和历史编辑均可立即撤销旧资格。 */
    private suspend fun checkAuthorized(token: AgentExecutionToken): Pair<AgentRun, ConversationToolPolicy> {
        val settings = settings(token.conversation)
        val run = mDao.run(token.runId) ?: throw AgentAuthorizationException()
        val policy = mDao.policy(token.conversation.key) ?: throw AgentAuthorizationException()
        if (!settings.enabled || settings.version != token.settingsVersion || policy.historyVersion != token.historyVersion ||
            policy.executionEpoch != token.executionEpoch || run.activeConversationKey != token.conversation.key || run.state.isTerminal ||
            run.state == AgentRunState.Cancelling || run.settingsVersion != token.settingsVersion || run.historyVersion != token.historyVersion ||
            run.executionEpoch != token.executionEpoch
        ) throw AgentAuthorizationException()
        if (run.state == AgentRunState.WaitingForUser && (run.expiresAt == null || run.expiresAt <= System.currentTimeMillis())) throw AgentAuthorizationException()
        // 人设删除、默认人设切换及成员移除也会让旧运行失效。
        validateIdentity(
            token.conversation,
            settings.personaId,
            run.characterId ?: throw AgentAuthorizationException(),
            run.personaId ?: throw AgentAuthorizationException()
        )
        return run to policy
    }

    /** 校验当前实际人设与发言角色，不信任过期的默认指针或群成员快照。 */
    private suspend fun validateIdentity(
        conversation: ConversationRef,
        boundPersonaId: Long,
        characterId: Long,
        personaId: Long
    ) {
        // FOLLOW_DEFAULT 在执行时重新解析，默认身份变化不能沿用旧权限。
        val actualPersonaId =
            if (boundPersonaId == UserPersona.FOLLOW_DEFAULT) mDatabase.getUserPersonaDao()
                .active().id else boundPersonaId
        if (actualPersonaId != personaId || mDatabase.getUserPersonaDao()
                .find(personaId) == null
        ) throw AgentAuthorizationException()
        val visible = when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatSessionDao()
                .getSessionById(conversation.id)?.characterId == characterId

            is ConversationRef.Group -> mDatabase.getGroupChatMemberDao()
                .getMembers(conversation.id).any { it.characterId == characterId }
        }
        if (!visible) throw AgentAuthorizationException()
    }

    private data class Settings(val enabled: Boolean, val version: Long, val personaId: Long)

    private suspend fun settings(conversation: ConversationRef): Settings = when (conversation) {
        is ConversationRef.Single -> mDatabase.getChatSessionDao().getSessionById(conversation.id)
            ?.let { Settings(it.agentEnabled, it.agentSettingsVersion, it.userPersonaId) }

        is ConversationRef.Group -> mDatabase.getGroupChatSessionDao()
            .getSessionById(conversation.id)
            ?.let { Settings(it.agentEnabled, it.agentSettingsVersion, it.userPersonaId) }
    } ?: throw AgentAuthorizationException()

    private suspend fun revokeActive(conversation: ConversationRef, state: AgentRunState) {
        mDao.activeRun(conversation.key)?.let { interrupt(it, state) }
    }

    private suspend fun interrupt(run: AgentRun, state: AgentRunState) {
        mDao.dispatchingInvocations(run.id)
            .forEach { mDao.updateInvocation(it.copy(state = ToolInvocationState.UnknownOutcome)) }
        mDao.updateRun(
            run.copy(
                state = state,
                activeConversationKey = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
