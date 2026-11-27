package me.kafuuneko.rpclient.libs.agent.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentRunState
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolResultCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.llm.ToolCallingCapabilityResolver
import me.kafuuneko.rpclient.libs.room.entity.toConfig
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.ChatRepository
import me.kafuuneko.rpclient.libs.room.repository.GroupChatRepository
import me.kafuuneko.rpclient.libs.room.repository.LLMRepository
import me.kafuuneko.rpclient.utils.jsonObject

/** 进程内后台初始化作用域；单次运行仍属于发起页面的结构化任务。 */
class AgentApplicationScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * 会话运行准入、撤权和等待恢复协调器。
 * - 初始化在后台回收异常中断运行，页面不在主线程等待数据库 IO。
 * - 停止先撤权再取消网络 Job，等待用户的运行不保留协程。
 * - 跨页重入由会话租约与数据库独占归属共同拦截。
 */
class AgentRunManager(
    private val mRuns: AgentRunRepository,
    private val mRuntime: AgentRuntime,
    private val mComposer: AgentPromptComposer,
    private val mStore: AgentCheckpointStore,
    private val mCodec: AgentCheckpointCodec,
    private val mProviders: LLMRepository,
    private val mChats: ChatRepository,
    private val mGroups: GroupChatRepository,
    private val mCapabilities: ToolCallingCapabilityResolver,
    private val mActivities: ConversationGenerationActivityRegistry,
    application: AgentApplicationScope,
) {
    private val mJobs = ConcurrentHashMap<String, Job>()
    private val mInitialization =
        application.scope.async {
            mRuns.recoverInterrupted()
            mStore.prune(mRuns.recoveryKeys())
        }

    /** 返回独立工具请求的同源预算检查信息，不影响普通生成的检查器快照。 */
    fun inspection(conversation: ConversationRef) = mComposer.inspection(conversation)

    /** 创建新运行，准备阶段固定身份、预设和模型配置。 */
    suspend fun run(input: AgentTurnInput): AgentTurnPlan = withContext(Dispatchers.IO) {
        // 先等待冷启动异常回收完成，再校验供应商启用与工具调用能力。
        mInitialization.await()
        if (!input.provider.isEnabled) throw AgentRuntimeException("provider_disabled")
        mCapabilities.requireSupported(input.provider.toConfig())
        val lease = mActivities.acquire(input.conversation)
        var token: AgentExecutionToken? = null
        try {
            // 占用会话独占运行并冻结本轮预设、工具快照与初始检查点。
            mJobs[input.conversation.key] = currentCoroutineContext().job
            val started =
                mRuns.start(input.conversation, input.characterId, input.personaId, input.batchId)
            token = started
            val preset = when (val conversation = input.conversation) {
                is ConversationRef.Single -> mChats.getSessionById(conversation.id)?.agentPromptPresetId
                is ConversationRef.Group -> mGroups.getSessionById(conversation.id)?.agentPromptPresetId
            }
            val checkpoint = AgentRuntimeCheckpoint(mComposer.prepare(input, preset, started))
            mRuntime.save(started, checkpoint)
            mRuntime.execute(started, checkpoint)
        } catch (error: Exception) {
            token?.let { finishOnFailure(it, error) }
            throw error
        } finally {
            // 退出时释放协程登记、临时预览与会话活动租约。
            mJobs.remove(input.conversation.key, currentCoroutineContext().job)
            mRuntime.release(input.conversation)
            mActivities.release(input.conversation, lease)
        }
    }

    /** 用户回答或显式确认后读取本机快照，重新核验权限、定义和配置指纹。 */
    suspend fun resume(
        conversation: ConversationRef,
        answer: String,
        confirmed: Boolean
    ): AgentTurnPlan = withContext(Dispatchers.IO) {
        mInitialization.await()
        val lease = mActivities.acquire(conversation)
        var token: AgentExecutionToken? = null
        try {
            // 重建运行令牌并加载本机检查点，复核供应商能力与配置指纹未漂移。
            mJobs[conversation.key] = currentCoroutineContext().job
            val current = mRuns.resumeToken(conversation)
            token = current
            val checkpoint = load(conversation)
            val provider = checkpoint.plan.input.provider.toConfig()
            mCapabilities.requireSupported(provider)
            if (AgentPromptComposer.providerDigest(checkpoint.plan.input.provider) != checkpoint.plan.providerDigest) throw AgentRuntimeException(
                "provider_changed"
            )
            // 区分普通提问回答与显式确认，回答仅写结果而确认重新打开待派发状态。
            val invocation = mRuns.invocations(current.runId)
                .firstOrNull { it.state == ToolInvocationState.AwaitingUser }
                ?: throw AgentRuntimeException("waiting_call_missing")
            if (invocation.awaitingKind == ToolUserRequestKind.Input.name) {
                require(answer.isNotBlank() && answer.length <= 4000) { "Invalid user answer" }
                val key = requireNotNull(invocation.checkpointKey)
                val payload = mStore.read(key, requireNotNull(invocation.resultDigest))
                    ?: throw AgentRuntimeException("checkpoint_missing")
                payload.add(
                    "result",
                    ToolResultCodec.encode(ToolResult.Success(jsonObject("answer" to answer)))
                )
                val digest = mStore.write(key, payload)
                mRuns.answerInput(current, invocation.id, key, digest)
            } else {
                if (!confirmed) {
                    mRuns.revoke(conversation)
                    throw CancellationException("User rejected confirmation")
                }
                mRuns.confirmInvocation(current, invocation.id)
            }
            mRuntime.execute(current, checkpoint)
        } catch (error: Exception) {
            token?.let { finishOnFailure(it, error) }
            throw error
        } finally {
            mJobs.remove(conversation.key, currentCoroutineContext().job)
            mRuntime.release(conversation)
            mActivities.release(conversation, lease)
        }
    }

    /** 退出页面保留已持久化等待状态，其他活动运行沿用停止规则。 */
    suspend fun leave(conversation: ConversationRef) {
        mInitialization.await()
        if (mRuns.active(conversation)?.state != AgentRunState.WaitingForUser) stop(conversation)
    }

    /** 先以短事务撤权，随后至多等待五秒本地取消收尾。 */
    suspend fun stop(conversation: ConversationRef) = withContext(Dispatchers.IO) {
        mInitialization.await()
        val partial = mRuntime.partial(conversation)
        mRuns.revokeWithPartial(
            conversation,
            partial?.token
        ) { partial?.let { mRuntime.persistPartial(it) } }
        val job = mJobs[conversation.key]
        job?.cancel()
        if (job != currentCoroutineContext().job) withTimeoutOrNull(5000) { job?.join() }
    }

    /** 整库替换前终止所有活动运行，包括无人展示的等待状态。 */
    suspend fun stopAll() = withContext(Dispatchers.IO) {
        mInitialization.await()
        mRuns.revokeAll()
        val jobs = mJobs.values.toList()
        jobs.forEach { it.cancel() }
        withTimeoutOrNull(5000) { jobs.forEach { it.join() } }
    }

    /** 恢复数据库完成后清理本机旧载荷，避免跨库 ID 碰撞。 */
    suspend fun clearCheckpoints() {
        mStore.clear()
        mRuntime.clear()
        mComposer.clearInspections()
        mComposer.clearTools()
    }

    /** 读取等待问题的可展示投影，不把原始参数与协议续接放入 UI。 */
    suspend fun waiting(conversation: ConversationRef): ToolResult.AwaitingUser? {
        mInitialization.await()
        mRuns.expireWaiting()
        val run = mRuns.active(conversation)?.takeIf { it.state == AgentRunState.WaitingForUser }
            ?: return null
        val invocation =
            mRuns.invocations(run.id).firstOrNull { it.state == ToolInvocationState.AwaitingUser }
                ?: return null
        val payload = mStore.read(
            invocation.checkpointKey ?: return null,
            invocation.resultDigest ?: return null
        ) ?: return null
        return ToolResultCodec.decode(payload.getAsJsonObject("result")) as? ToolResult.AwaitingUser
    }

    /** 获取冻结队列和模板，群聊在恢复当前成员后继续原批次剩余成员。 */
    suspend fun load(conversation: ConversationRef): AgentRuntimeCheckpoint {
        val run = mRuns.active(conversation) ?: throw AgentRuntimeException("run_missing")
        val payload = mStore.read(
            run.checkpointKey ?: throw AgentRuntimeException("checkpoint_missing"),
            run.checkpointDigest ?: throw AgentRuntimeException("checkpoint_missing")
        ) ?: throw AgentRuntimeException("checkpoint_missing")
        val provider =
            mProviders.getProviderById(payload["providerId"].asLong) ?: throw AgentRuntimeException(
                "provider_missing"
            )
        val checkpoint = mCodec.decode(payload, provider)
        mComposer.retainTools(run.id, checkpoint.plan.toolDigests.values)
        mComposer.refreshTools()
        if (checkpoint.plan.input.conversation != conversation) throw AgentRuntimeException("checkpoint_owner_mismatch")
        return checkpoint
    }

    private suspend fun finishOnFailure(token: AgentExecutionToken, error: Exception) =
        withContext(NonCancellable + Dispatchers.IO) {
            if (error is AgentWaitingForUser) return@withContext
            // 旧库或旧运行的迟到收尾只能终止自身，不能撤销之后启动的新占有者。
            mRuns.abortOwned(
                token,
                if (error is CancellationException) AgentRunState.Cancelled else AgentRunState.Failed
            )
            mComposer.releaseTools(token.runId)
        }
}
