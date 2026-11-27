package me.kafuuneko.rpclient.libs.agent.tool.builtin.scene

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.SceneStateCodec
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.agent.tool.base.LocalChangePlan
import me.kafuuneko.rpclient.libs.agent.tool.proposal.LocalProposalHandler
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.libs.room.repository.SceneStateRepository
import me.kafuuneko.rpclient.utils.jsonObject

/** 剧情变更的准备、预览与提案提交服务，保持逐字证据和乐观修订约束。 */
class SceneStateService(
    private val mData: AgentLocalDataRepository,
    private val mScenes: SceneStateRepository
) : LocalProposalHandler {
    override val operations = setOf(OPERATION)

    /** 场景读取叠加本次尚未提交的增量，并明确标记预览状态。 */
    suspend fun scene(context: ToolExecutionContext): JsonObject {
        val state = mScenes.get(context.token.conversation)
        val proposals =
            mData.proposals(context.token.runId).filter { it["operation"].asString == OPERATION }
        var json = state.stateJson
        proposals.forEach { json = SceneStateCodec.apply(json, changes(it)) }
        return jsonObject(
            "revision" to state.revision + proposals.size,
            "state" to AgentJson.parse(json),
            "proposed" to proposals.isNotEmpty()
        )
    }


    /** @return 已验证的剧情提案，不在准备阶段修改持久化状态。 */
    suspend fun planChange(context: ToolExecutionContext, arguments: JsonObject): LocalChangePlan {
        val proposal = arguments.deepCopy().apply {
            addProperty("operation", OPERATION)
            addProperty("proposalId", context.invocationId)
        }
        // 先核验证据，再使用叠加视图检查依赖的修订与路径约束。
        val immediate =
            mData.validateEvidence(context, proposal, dependsOnPending(context, proposal))
        val state = scene(context)
        if (state["revision"].asLong != proposal["expectedRevision"].asLong) {
            throw ToolPreparationException(ToolErrorCode.SCENE_REVISION_CONFLICT)
        }
        try {
            SceneStateCodec.apply(state["state"].toString(), changes(proposal))
        } catch (error: CancellationException) {
            throw error
        } catch (error: ToolPreparationException) {
            throw error
        } catch (_: JsonParseException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        } catch (_: IOException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        } catch (_: IllegalArgumentException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        } catch (_: IllegalStateException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        }
        val result = ToolResult.Success(
            jsonObject("proposal" to proposal),
            if (immediate) ToolWriteDisposition.Committed else ToolWriteDisposition.Proposed
        )
        return LocalChangePlan(result) {
            if (immediate) check(
                commit(
                    context,
                    proposal,
                    proposal["sourceMessageId"].asLong
                )
            ) { "Evidence rejected" }
        }
    }

    /** @return 当前提案是否依赖尚未持久化的剧情修订。 */
    override suspend fun dependsOnPending(
        context: ToolExecutionContext,
        proposal: JsonObject
    ): Boolean =
        proposal["expectedRevision"]?.takeUnless { it.isJsonNull }?.asLong?.let {
            mScenes.get(context.token.conversation).revision != it
        } ?: false

    /** @return 证据及前序修订均满足时提交成功，否则跳过不受正文支持的提案。 */
    override suspend fun commit(
        context: ToolExecutionContext,
        proposal: JsonObject,
        finalMessageId: Long
    ): Boolean {
        val source = mData.proposalSource(context, proposal, finalMessageId) ?: return false
        val proposed = proposal["sourceMessageId"]?.asLong == 0L
        val expected = proposal["expectedRevision"].asLong
        // 前序提案被拒绝后，不能将依赖它的后续增量强行应用到旧状态。
        if (proposed && mScenes.get(context.token.conversation).revision != expected) return false
        mScenes.applyAgentProposal(
            context.token.conversation,
            expected,
            changes(proposal),
            source.id
        )
        return true
    }

    /** 解析类型化路径及 JSON 字面量，再交给领域 Codec 验证半结构化约束。 */
    private fun changes(proposal: JsonObject): List<SceneStateChange> =
        proposal.getAsJsonArray("changes").map { element ->
            val change = element.asJsonObject
            SceneStateChange(
                change.getAsJsonArray("path").map { it.asString },
                change["value_json"]?.takeUnless { it.isJsonNull }?.asString?.let {
                    AgentJson.parse(it)
                })
        }


    companion object {
        const val OPERATION = "update_scene_state"
    }
}
