package me.kafuuneko.rpclient.libs.agent.tool.builtin.memory

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.tool.ToolErrorCode
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.agent.tool.base.LocalChangePlan
import me.kafuuneko.rpclient.libs.agent.tool.proposal.LocalProposalHandler
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryRestoreValidator
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTable
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.libs.room.repository.MemoryRepository
import me.kafuuneko.rpclient.utils.jsonObject

/** 记忆操作的持久化身份，类名变化不改变检查点编码。 */
enum class MemoryOperation(val id: String) {
    Upsert("upsert_memory"), Correct("correct_memory"), Invalidate("invalidate_memory"), Promote("promote_memory_scope")
}

/**
 * 记忆写入领域服务，同时承担准备、叠加预览和最终回复提案提交。
 * - 不持有当前调用状态，不自行开启跨网络或检查点 IO 的事务。
 * - 用户证据即时提交，待正文证据仅保存提案。
 */
class MemoryWriteService(
    private val mData: AgentLocalDataRepository,
    private val mMemories: MemoryRepository,
    private val mRuns: AgentRunRepository,
) : LocalProposalHandler {
    override val operations = MemoryOperation.entries.mapTo(linkedSetOf()) { it.id }

    /** 在已授权记忆之上叠加当前运行提案，预览修订不会被误报为永久保存。 */
    suspend fun memoryView(context: ToolExecutionContext): List<JsonObject> {
        // 先加载数据库内当前会话可见的已提交记忆快照。
        val view = mData.memories(context).map { entry ->
            jsonObject(
                "memoryId" to entry.ref.id,
                "table" to entry.ref.table.name,
                "revision" to entry.fields.currentRevision,
                "kind" to entry.fields.content.kind,
                "status" to entry.fields.content.status,
                "body" to entry.fields.content.body,
                "proposed" to false
            )
        }.toMutableList()
        // 按执行顺序叠加本轮未提交提案，显式标记 proposed 与预览修订号。
        mData.proposals(context.token.runId).forEach { proposal ->
            val operation = proposal["operation"].asString
            if (operation !in operations || operation == MemoryOperation.Promote.id
            ) return@forEach
            val old = view.firstOrNull {
                it["memoryId"] == proposal["memoryId"] && it["table"] == proposal["table"] && proposal.has(
                    "memoryId"
                )
            }
            val next = old?.deepCopy() ?: jsonObject(
                "kind" to proposal["kind"].asString,
                "status" to MemoryStatus.Active.name
            )
            proposal["body"]?.let { next.add("body", it.deepCopy()) }
            proposal["kind"]?.let { next.add("kind", it.deepCopy()) }
            next.addProperty(
                "status",
                if (operation == MemoryOperation.Invalidate.id) MemoryStatus.Invalidated.name else MemoryStatus.Active.name
            )
            next.addProperty("revision", (proposal["expectedRevision"]?.asLong ?: 0) + 1)
            next.addProperty("proposed", true)
            proposal["proposalId"]?.let { next.add("proposalId", it.deepCopy()) }
            if (old != null) view.remove(old)
            view.add(next)
        }
        return view
    }

    /** 规划记忆变更，先校验证据、可见性及乐观版本，提交由统一执行器负责。 */
    suspend fun planChange(
        context: ToolExecutionContext,
        arguments: JsonObject,
        operation: MemoryOperation,
    ): LocalChangePlan {
        val proposal = arguments.deepCopy().apply { addProperty("operation", operation.id) }
        try {
            // 新建记忆先归一化表名和 0 版本等常见冗余参数，再进入证据与去重校验。
            if (operation == MemoryOperation.Upsert && !hasMemoryId(proposal)) {
                normalizeNewMemoryIdentity(context, proposal)
            }
            val immediate =
                mData.validateEvidence(context, proposal, dependsOnPending(context, proposal))
            proposal.addProperty("proposalId", context.invocationId)
            if (operation == MemoryOperation.Upsert && !hasMemoryId(proposal)) {
                reuseDuplicate(context, proposal)?.let { return it }
            }
            validateMemoryWrite(
                context,
                proposal,
                immediate
            )
            val confirmation = promotionConfirmation(context, proposal, operation)
            val result = ToolResult.Success(
                jsonObject("proposal" to proposal),
                if (immediate) ToolWriteDisposition.Committed else ToolWriteDisposition.Proposed
            )
            return LocalChangePlan(result, confirmation) {
                if (immediate) check(
                    commit(
                        context,
                        proposal,
                        proposal["sourceMessageId"].asLong
                    )
                ) { "Evidence rejected" }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ToolPreparationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        } catch (_: IllegalStateException) {
            throw ToolPreparationException(ToolErrorCode.INVALID_EVIDENCE_OR_REVISION)
        }
    }

    /** 同轮重复提案直接复用，已保存同内容条目转换为带版本的幂等更新。 */
    private suspend fun reuseDuplicate(
        context: ToolExecutionContext,
        proposal: JsonObject
    ): LocalChangePlan? {
        // 同轮已提出相同内容时直接复用原提案，避免重复创建两条记忆。
        val duplicate = mData.proposals(context.token.runId).firstOrNull {
            it["operation"].asString == MemoryOperation.Upsert.id && it["body"] == proposal["body"] && it["kind"] == proposal["kind"] && !it.has(
                "memoryId"
            )
        }
        if (duplicate != null) return LocalChangePlan(
            ToolResult.Success(
                jsonObject("proposal" to duplicate),
                ToolWriteDisposition.Proposed
            )
        ) { }
        // 已存在完全同内容同分类的会话记忆时转为幂等更新，避免产生重复条目。
        mData.memories(context)
            .firstOrNull { entry -> entry.ref.table.name != MemoryTable.Character.name && entry.fields.content.body == proposal["body"].asString && entry.fields.content.kind == proposal["kind"].asString }
            ?.let { existing ->
                proposal.addProperty("memoryId", existing.ref.id)
                proposal.addProperty("table", existing.ref.table.name)
                proposal.addProperty("expectedRevision", existing.fields.currentRevision)
            }
        return null
    }

    /** 提升范围时保留事件与关系的强制确认，自动提升策略不能放宽这两类事实。 */
    private suspend fun promotionConfirmation(
        context: ToolExecutionContext,
        proposal: JsonObject,
        operation: MemoryOperation
    ): ToolResult.AwaitingUser? {
        // 记忆范围提升默认要求用户显式确认；即便开启自动提升，事件与关系仍必须确认。
        return if (operation == MemoryOperation.Promote) {
            val entry = mData.memories(context)
                .firstOrNull { it.ref.id == proposal["memoryId"].asLong && it.ref.table.name == proposal["table"].asString }
                ?: throw ToolPreparationException(ToolErrorCode.MEMORY_NOT_VISIBLE)
            if (!mRuns.policy(context.token.conversation).autoPromoteMemory || entry.fields.content.kind in setOf(
                    MemoryKind.event.name,
                    MemoryKind.relationship.name
                )
            ) ToolResult.AwaitingUser(
                "",
                kind = ToolUserRequestKind.LocalWriteConfirmation,
                details = jsonObject(
                    "operation" to operation.id,
                    "excerpt" to entry.fields.content.body.take(1000)
                )
            ) else null
        } else null
    }

    /** 检查提案是否指定了非空目标记忆主键。 */
    private fun hasMemoryId(proposal: JsonObject): Boolean =
        proposal["memoryId"]?.takeUnless { it.isJsonNull } != null

    /** 新建记忆时兼容当前会话表名与 0 版本，仍拒绝跨表、角色表和大于 0 的孤立版本。 */
    private fun normalizeNewMemoryIdentity(
        context: ToolExecutionContext,
        proposal: JsonObject,
    ) {
        // 校验可选表名必须属于当前会话表，禁止新建时误指角色只读表或另一类会话表。
        val expectedTable = when (context.token.conversation) {
            is ConversationRef.Single -> MemoryTable.Chat.name
            is ConversationRef.Group -> MemoryTable.Group.name
        }
        val table = proposal["table"]?.takeUnless { it.isJsonNull }?.asString
        if (table != null && table != expectedTable) {
            throw ToolPreparationException(
                if (table == MemoryTable.Character.name) ToolErrorCode.INHERITED_MEMORY_READ_ONLY else ToolErrorCode.INVALID_MEMORY_TABLE
            )
        }
        // 新建时仅允许省略或传 0 版本，非零版本缺少 memoryId 必须拒绝。
        val revision = proposal["expectedRevision"]?.takeUnless { it.isJsonNull }?.asLong
        if (revision != null && revision != 0L) {
            throw ToolPreparationException(ToolErrorCode.INCOMPLETE_MEMORY_IDENTITY)
        }
        proposal.remove("memoryId")
        proposal.remove("table")
        proposal.remove("expectedRevision")
    }

    /** 参数的条件必填和乐观版本在准备阶段拒绝；未发生的剧情不能取得通用记忆提升资格。 */
    private suspend fun validateMemoryWrite(
        context: ToolExecutionContext,
        proposal: JsonObject,
        immediate: Boolean
    ) {
        val id = proposal["memoryId"]?.takeUnless { it.isJsonNull }?.asLong
        if (id == null) {
            if (proposal.has("table") || proposal.has("expectedRevision")) {
                throw ToolPreparationException(ToolErrorCode.INCOMPLETE_MEMORY_IDENTITY)
            }
            return
        }
        if (!proposal.has("table") || !proposal.has("expectedRevision")) {
            throw ToolPreparationException(ToolErrorCode.INCOMPLETE_MEMORY_IDENTITY)
        }
        // 目标记忆必须存在于当前可见集，防止跨会话主键越权。
        val entry = mData.memories(context)
            .firstOrNull { it.ref.id == id && it.ref.table.name == proposal["table"].asString }
            ?: throw ToolPreparationException(ToolErrorCode.MEMORY_NOT_VISIBLE)
        val view = memoryView(context)
            .first { it["memoryId"]?.asLong == id && it["table"]?.asString == entry.ref.table.name }
        if (view["status"].asString != MemoryStatus.Active.name) {
            throw ToolPreparationException(ToolErrorCode.MEMORY_PENDING_INVALIDATION)
        }
        if (immediate && view["revision"].asLong != entry.fields.currentRevision) {
            throw ToolPreparationException(ToolErrorCode.MEMORY_REVISION_CONFLICT)
        }
        // 范围提升必须使用已提交证据且只能从会话表提升。
        if (proposal["operation"].asString == MemoryOperation.Promote.id) {
            if (entry.ref.table.name == MemoryTable.Character.name) {
                throw ToolPreparationException(ToolErrorCode.INHERITED_MEMORY_READ_ONLY)
            }
            if (!immediate) {
                throw ToolPreparationException(ToolErrorCode.PROMOTION_REQUIRES_COMMITTED_EVIDENCE)
            }
            if (entry.fields.currentRevision != proposal["expectedRevision"].asLong) {
                throw ToolPreparationException(ToolErrorCode.MEMORY_REVISION_CONFLICT)
            }
            return
        }
        if (entry.ref.table.name == MemoryTable.Character.name) {
            throw ToolPreparationException(ToolErrorCode.INHERITED_MEMORY_READ_ONLY)
        }
        // 连续提案使用叠加视图的版本；已提交来源不能越过尚未发生的前序变更。
        val visible = view["revision"].asLong
        if (visible != proposal["expectedRevision"].asLong) {
            throw ToolPreparationException(ToolErrorCode.MEMORY_REVISION_CONFLICT)
        }
    }

    /** 检查乐观版本是否依赖待提交记忆提案，防止越过前序变更。 */
    override suspend fun dependsOnPending(
        context: ToolExecutionContext,
        proposal: JsonObject,
    ): Boolean {
        val expected =
            proposal["expectedRevision"]?.takeUnless { it.isJsonNull }?.asLong ?: return false
        val memoryId = proposal["memoryId"]?.takeUnless { it.isJsonNull }?.asLong ?: return false
        val table = proposal["table"]?.takeUnless { it.isJsonNull }?.asString ?: return false
        val existing =
            mData.memories(context)
                .firstOrNull { it.ref.id == memoryId && it.ref.table.name == table }
                ?: return false
        return existing.fields.currentRevision != expected
    }

    /** 短事务内复核证据与修订，继承条目只能通过显式提升授权写入。 */
    override suspend fun commit(
        context: ToolExecutionContext,
        proposal: JsonObject,
        finalMessageId: Long
    ): Boolean {
        val source = mData.proposalSource(context, proposal, finalMessageId) ?: return false
        val sourceId = source.id
        val proposed = proposal["sourceMessageId"]?.asLong == 0L
        val quote = proposal["evidence"].asString
        val operation = proposal["operation"].asString
        // 引用只能来自当前可见集；写入继承条目必须显式提升授权，不能跨表偷偷改通用记忆。
        val existing = proposal["memoryId"]?.asLong?.takeIf { it > 0 }?.let { id ->
            mData.memories(context)
                .firstOrNull { it.ref.id == id && it.ref.table.name == proposal["table"]?.asString }
                ?: error("Memory is not visible")
        }
        if (operation == MemoryOperation.Promote.id) {
            requireNotNull(existing)
            mMemories.promote(
                existing.target,
                existing.ref,
                proposal["expectedRevision"].asLong,
                context.characterId
            )
            return true
        }
        require(existing == null || existing.target == mData.target(context)) { "Inherited memory is read only" }
        if (proposed && existing != null && existing.fields.currentRevision != proposal["expectedRevision"].asLong) return false
        val body =
            if (operation == MemoryOperation.Invalidate.id) requireNotNull(existing).fields.content.body else proposal["body"].asString
        val draft = MemoryDraft(
            body = body,
            kind = proposal["kind"]?.asString?.let(MemoryKind::valueOf)
                ?: existing?.fields?.content?.kind?.let(MemoryKind::valueOf) ?: MemoryKind.fact,
            status = if (operation == MemoryOperation.Invalidate.id) MemoryStatus.Invalidated else MemoryStatus.Active,
            importance = existing?.fields?.content?.importance ?: 50,
            pinned = existing?.fields?.content?.pinned ?: false,
            personaId = existing?.fields?.personaId ?: context.personaId,
            privateCharacterId = existing?.privateCharacterId
                ?: if (context.token.conversation is ConversationRef.Group) context.characterId else null
        )
        val evidence = MemorySourceFields(
            0,
            if (context.token.conversation is ConversationRef.Single) "ChatMessage" else "GroupMessage",
            "Observed",
            sourceKey = "${context.token.conversation.key}/$sourceId",
            chatMessageId = sourceId.takeIf { context.token.conversation is ConversationRef.Single },
            groupMessageId = sourceId.takeIf { context.token.conversation is ConversationRef.Group },
            authorKey = if (source.user) "user/${context.personaId}" else "character/${context.characterId}",
            fingerprint = MemoryRestoreValidator.fingerprint(source.content),
            quote = quote,
            rangeStart = source.content.indexOf(quote),
            rangeEnd = source.content.indexOf(quote) + quote.length,
            originRef = context.invocationId,
            createdAt = System.currentTimeMillis()
        )
        mMemories.saveAgent(
            mData.target(context),
            draft,
            existing?.ref,
            proposal["expectedRevision"]?.asLong ?: 0,
            listOf(evidence)
        )
        return true
    }

}
