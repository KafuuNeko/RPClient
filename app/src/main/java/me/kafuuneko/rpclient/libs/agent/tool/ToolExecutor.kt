package me.kafuuneko.rpclient.libs.agent.tool

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.kafuuneko.rpclient.libs.agent.model.AgentExecutionToken
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.ToolInvocationState
import me.kafuuneko.rpclient.libs.agent.runtime.AgentCheckpointStore
import me.kafuuneko.rpclient.libs.room.entity.ToolInvocation
import me.kafuuneko.rpclient.libs.room.repository.AgentAuthorizationException
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository

/** 一轮内的客户端调用序号不使用模型 callId，定义版本与指纹来自冻结请求。 */
data class ToolCallRequest(
    val stepId: Long,
    val ordinal: Int,
    val toolId: String,
    val contractVersion: Int,
    val definitionDigest: String,
    val argumentsJson: String,
    val remoteCallId: String? = null,
    val independent: Boolean = false,
)

/**
 * 所有工具共用的强校验与执行入口。
 * - 调用前复核身份、工具白名单、记忆写入模式和参数 Schema。
 * - 结果先发布检查点，本地写入与完成标记在一个短事务提交。
 * - 最多两个独立确定性读取并发，其余动作保持模型原序。
 */
class ToolExecutor(
    private val mRegistry: ToolRegistry,
    private val mRuns: AgentRunRepository,
    private val mCheckpoints: AgentCheckpointStore,
) {
    /** 按序执行本轮调用；等待用户或致命错误后不再派发后续动作。 */
    suspend fun executeBatch(
        token: AgentExecutionToken,
        calls: List<ToolCallRequest>
    ): List<ToolResult> = coroutineScope {
        require(calls.map { it.ordinal }
            .distinct().size == calls.size) { "Duplicate invocation ordinal" }
        val results = mutableListOf<ToolResult>()
        var index = 0
        // 只合并相邻、明确声明独立的确定性读取，不将写入越过前序调用调度。
        while (index < calls.size) {
            val first = calls[index]
            val second = calls.getOrNull(index + 1)
            val count = if (second != null && canParallel(first) && canParallel(second)) 2 else 1
            val batch =
                calls.subList(index, index + count).map { call -> async { execute(token, call) } }
                    .awaitAll()
            results += batch
            if (batch.any { it is ToolResult.AwaitingUser || it is ToolResult.FatalError }) break
            index += count
        }
        results
    }

    /** 执行或回放一个调用，任何取消都向结构化协程上抛。 */
    suspend fun execute(token: AgentExecutionToken, call: ToolCallRequest): ToolResult {
        val tool = mRegistry.find(call.toolId) ?: return ToolResult.RecoverableError("unknown_tool")
        if (tool.definition.contractVersion != call.contractVersion || tool.digest != call.definitionDigest) return ToolResult.FatalError(
            "tool_contract_changed"
        )
        if (call.ordinal < 0) return ToolResult.FatalError("invalid_invocation_ordinal")
        val arguments = runCatching { AgentJson.parse(call.argumentsJson).asJsonObject }.getOrNull()
            ?: return ToolResult.RecoverableError("invalid_tool_arguments")
        if (ToolSchemaValidator.validate(tool.definition.parameters, arguments)
                .isNotEmpty()
        ) return ToolResult.RecoverableError("invalid_tool_arguments")
        val invocationId = AgentJson.digest("${token.runId}/${call.stepId}/${call.ordinal}")
        var dispatched = false
        try {
            // 首次派发持久化客户端身份，重试始终复核同一参数与定义指纹。
            val context = mRuns.toolContext(token, call.stepId, invocationId)
            val claim = mRuns.claimInvocation(
                token, ToolInvocation(
                    id = invocationId,
                    runId = token.runId,
                    stepId = call.stepId,
                    ordinal = call.ordinal,
                    remoteCallId = call.remoteCallId,
                    toolId = call.toolId,
                    contractVersion = call.contractVersion,
                    definitionDigest = tool.digest,
                    argumentsDigest = AgentJson.digest(AgentJson.canonical(arguments)),
                    createdAt = System.currentTimeMillis(),
                ), tool.definition
            )
            if (claim.invocation.state != ToolInvocationState.Prepared) return replay(claim.invocation)
            val policy = mRuns.policy(token.conversation)
            val result = withTimeoutOrNull(policy.toolTimeoutMillis) {
                executePrepared(token, tool, claim.invocation, arguments, context) {
                    dispatched = true
                }
            }
            if (result == null && dispatched) mRuns.markUnknown(invocationId)
            return result ?: ToolResult.FatalError("tool_timeout")
        } catch (error: CancellationException) {
            // 取消之后仍完成少量本地状态收尾，不吞掉上层取消信号。
            if (dispatched) withContext(NonCancellable) { mRuns.markUnknown(invocationId) }
            throw error
        } catch (_: AgentAuthorizationException) {
            if (dispatched) mRuns.markUnknown(invocationId)
            return ToolResult.FatalError("tool_authorization_revoked")
        } catch (_: Exception) {
            if (dispatched) mRuns.markUnknown(invocationId)
            return ToolResult.FatalError("tool_execution_failed")
        }
    }

    /** 在事务外准备与执行，只有本地 commit 进入持久化短事务。 */
    private suspend fun executePrepared(
        token: AgentExecutionToken,
        tool: RegisteredAgentTool,
        invocation: ToolInvocation,
        arguments: JsonObject,
        context: ToolExecutionContext,
        onDispatched: () -> Unit,
    ): ToolResult {
        val action = try {
            tool.handler.prepare(context, arguments.deepCopy())
        } catch (error: ToolPreparationException) {
            // 准备失败也发布确定结果，恢复不能因后续状态变更把旧错误重新执行成写入。
            mRuns.dispatchInvocation(token, invocation.id, tool.definition)
            onDispatched()
            return saveResult(
                token,
                tool,
                invocation,
                arguments,
                ToolResult.RecoverableError(error.code)
            )
        }
        validateAction(tool.definition, action)
        // 外部写入先展示固定目标与受控参数摘要，普通提问不能授予外发权限。
        if (action is ToolAction.ExternalWrite && !invocation.confirmationGranted) {
            val request = ToolResult.AwaitingUser(
                question = "",
                kind = ToolUserRequestKind.ExternalWriteConfirmation,
                details = JsonObject().apply {
                    addProperty("destinationHost", action.destinationHost)
                    add("parameters", action.parameterSummary.deepCopy())
                },
            )
            return saveResult(token, tool, invocation, arguments, request)
        }
        if (action is ToolAction.LocalWrite && action.confirmation != null && !invocation.confirmationGranted) {
            return saveResult(token, tool, invocation, arguments, action.confirmation)
        }
        if (action is ToolAction.AwaitingUser) return saveResult(
            token,
            tool,
            invocation,
            arguments,
            action.result
        )
        mRuns.dispatchInvocation(token, invocation.id, tool.definition)
        onDispatched()
        val result = when (action) {
            is ToolAction.Read -> action.execute()
            is ToolAction.ExternalWrite -> action.execute()
            is ToolAction.LocalWrite -> action.result
            is ToolAction.AwaitingUser -> error("Unexpected waiting action")
        }
        val commit = (action as? ToolAction.LocalWrite)?.commit ?: {}
        return saveResult(token, tool, invocation, arguments, result, commit)
    }

    private suspend fun replay(invocation: ToolInvocation): ToolResult {
        if (invocation.state in setOf(
                ToolInvocationState.Dispatching,
                ToolInvocationState.UnknownOutcome
            )
        ) return ToolResult.FatalError("tool_outcome_unknown")
        val key =
            invocation.checkpointKey ?: return ToolResult.FatalError("tool_checkpoint_missing")
        val digest =
            invocation.resultDigest ?: return ToolResult.FatalError("tool_checkpoint_missing")
        val payload = mCheckpoints.read(key, digest)
            ?: return ToolResult.FatalError("tool_checkpoint_missing")
        return runCatching { ToolResultCodec.decode(payload.getAsJsonObject("result")) }.getOrElse {
            ToolResult.FatalError(
                "tool_checkpoint_invalid"
            )
        }
    }

    private suspend fun saveResult(
        token: AgentExecutionToken,
        tool: RegisteredAgentTool,
        invocation: ToolInvocation,
        arguments: JsonObject,
        result: ToolResult,
        commit: suspend () -> Unit = {},
    ): ToolResult {
        // 写入结果超限时不能执行 commit，避免界面报错但实际已悄悄修改数据。
        val encoded = runCatching { ToolResultCodec.encode(result) }.getOrNull()
        require(encoded != null || tool.definition.effect != ToolEffect.LocalWrite) { "Local write result is too large" }
        val accepted =
            if (encoded == null) ToolResult.RecoverableError("tool_result_too_large") else result
        val payload = JsonObject().apply {
            add("definition", AgentJson.parse(tool.snapshotJson, maxDepth = 32))
            add("arguments", arguments.deepCopy())
            add("result", encoded ?: ToolResultCodec.encode(accepted))
        }
        val key = "invocation/${invocation.id}"
        val digest = mCheckpoints.write(key, payload)
        val state = when (accepted) {
            is ToolResult.Success -> ToolInvocationState.Completed
            is ToolResult.AwaitingUser -> ToolInvocationState.AwaitingUser
            else -> ToolInvocationState.Failed
        }
        mRuns.completeInvocation(
            token = token,
            invocationId = invocation.id,
            definition = tool.definition,
            resultDigest = digest,
            checkpointKey = key,
            state = state,
            writeDisposition = (accepted as? ToolResult.Success)?.writeDisposition?.name,
            awaitingKind = (accepted as? ToolResult.AwaitingUser)?.kind?.name,
            commit = commit,
        )
        return accepted
    }

    private fun validateAction(definition: AgentToolDefinition, action: ToolAction) {
        val valid = when (action) {
            is ToolAction.Read -> definition.effect in setOf(
                ToolEffect.ReadOnly,
                ToolEffect.NonDeterministicRead
            )

            is ToolAction.LocalWrite -> definition.effect == ToolEffect.LocalWrite && action.result.writeDisposition != null
            is ToolAction.ExternalWrite -> definition.effect == ToolEffect.ExternalWrite
            is ToolAction.AwaitingUser -> true
        }
        check(valid) { "Tool action does not match declared effect" }
    }

    private fun canParallel(call: ToolCallRequest): Boolean = call.independent &&
        mRegistry.find(call.toolId)?.definition?.let { it.effect == ToolEffect.ReadOnly && it.parallelSafe } == true
}
