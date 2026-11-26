package me.kafuuneko.rpclient.libs.room.repository

import com.google.gson.JsonObject
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateChange
import me.kafuuneko.rpclient.libs.agent.model.SceneStateCodec
import me.kafuuneko.rpclient.libs.agent.runtime.AgentCheckpointStore
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.groupchat.cardMembers
import me.kafuuneko.rpclient.libs.memory.MemoryDraft
import me.kafuuneko.rpclient.libs.memory.MemoryEntry
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.memory.MemoryRestoreValidator
import me.kafuuneko.rpclient.libs.memory.MemoryStatus
import me.kafuuneko.rpclient.libs.memory.MemoryTarget
import me.kafuuneko.rpclient.libs.room.AppDatabase
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry
import me.kafuuneko.rpclient.libs.room.entity.MemorySourceFields
import me.kafuuneko.rpclient.libs.search.SearchCursor
import me.kafuuneko.rpclient.libs.search.SearchEngineKind
import me.kafuuneko.rpclient.libs.search.SearchKind
import me.kafuuneko.rpclient.libs.search.SearchRequest
import me.kafuuneko.rpclient.libs.search.SearchScope
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/**
 * 内置工具的数据边界，只暴露当前运行获准的会话、角色和实际人设。
 * - 读取与分页复用领域仓库；消息归属和证据在写入事务中再次校验。
 * - 待生成剧情的写入仅存提案，最终正文接受后才绑定真实消息来源。
 */
class AgentLocalDataRepository(
    private val mDatabase: AppDatabase,
    private val mChats: ChatRepository,
    private val mGroups: GroupChatRepository,
    private val mLorebooks: LorebookRepository,
    private val mMemories: MemoryRepository,
    private val mScenes: SceneStateRepository,
    private val mSearch: ContentSearchRepository,
    private val mCheckpoints: AgentCheckpointStore,
) {
    /** 读取当前会话准许的世界书条目，执行时重新核对启用状态。 */
    suspend fun lore(context: ToolExecutionContext): List<LorebookEntry> {
        val conversation = context.token.conversation
        val ids = when (conversation) {
            is ConversationRef.Single -> mChats.getSessionLorebookEntryIds(
                requireNotNull(
                    mChats.getSessionById(
                        conversation.id
                    )
                )
            )

            is ConversationRef.Group -> mGroups.getSessionLorebookEntryIds(
                requireNotNull(
                    mDatabase.getGroupChatSessionDao().getSessionById(conversation.id)
                )
            )
        }.toSet()
        val characterBooks = when (conversation) {
            is ConversationRef.Single -> listOfNotNull(
                mDatabase.getCharacterDao()
                    .getCharacterById(context.characterId)?.characterLorebookId
            ).toSet()

            is ConversationRef.Group -> {
                val session = requireNotNull(mGroups.getSessionById(conversation.id))
                val members = mDatabase.getGroupChatMemberDao().getMembers(conversation.id)
                    .mapNotNull { member ->
                        mDatabase.getCharacterDao().getCharacterById(member.characterId)
                            ?.let { GroupChatMemberData(member, it) }
                    }
                val speaker = requireNotNull(
                    mDatabase.getCharacterDao().getCharacterById(context.characterId)
                )
                session.cardMembers(members, speaker).map { it.character.characterLorebookId }
                    .toSet()
            }
        }
        // 只加载已授权的条目和角色书，避免每次工具读取遍历整个世界书仓库。
        val selected = ids.toList().chunked(250)
            .flatMap { mDatabase.getLorebookEntryDao().getEntriesByIds(it) }
        val cards =
            characterBooks.filter { it > 0 }.flatMap { mLorebooks.getEntriesByLorebookId(it) }
        return (selected + cards).distinctBy { it.id }.filterNot { it.disabled }
    }

    /** 搜索范围固定在当前会话，游标含索引版本，不接受模型指定其他会话。 */
    suspend fun searchChat(
        context: ToolExecutionContext,
        query: String,
        cursor: JsonObject?
    ): ToolResult.Success {
        val conversation = context.token.conversation
        val page = mSearch.search(
            SearchRequest(
                query,
                if (conversation is ConversationRef.Single) SearchKind.Chat else SearchKind.Group,
                SearchScope.Body,
                conversation.id
            ), cursor?.let {
                SearchCursor(
                    it["revision"].asLong,
                    it["phase"].asInt,
                    it["time"].asLong,
                    it["kind"].asInt,
                    it["id"].asLong,
                    it["sourceId"].asLong,
                    SearchEngineKind.valueOf(it["engine"].asString),
                    it["session"].asString
                )
            })
        val next = page.next?.let {
            jsonObject(
                "revision" to it.revision,
                "phase" to it.phase,
                "time" to it.time,
                "kind" to it.kind,
                "id" to it.id,
                "sourceId" to it.sourceId,
                "engine" to it.engine.name,
                "session" to it.session
            )
        }?.toString()
        return ToolResult.Success(
            jsonObject(
                "items" to jsonArray(
                    page.hits.take(20).map {
                        jsonObject(
                            "messageId" to it.sourceId,
                            "excerpt" to it.snippet.take(250)
                        )
                    }), "complete" to page.complete
            ), truncated = next != null, nextCursor = next
        )
    }

    /** 读取当前会话的一条已提交消息，摘要和空占位不作为证据。 */
    suspend fun message(conversation: ConversationRef, id: Long): AgentMessage =
        when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatMessageDao().getMessageById(id)
                ?.takeIf { it.sessionId == conversation.id && it.source.name != "Summary" }
                ?.let { AgentMessage(it.id, it.source.name == "User", it.content) }

            is ConversationRef.Group -> mDatabase.getGroupChatMessageDao().getMessageById(id)
                ?.takeIf { it.sessionId == conversation.id }
                ?.let { AgentMessage(it.id, it.source.name == "User", it.content) }
        }?.takeIf { it.content.isNotBlank() } ?: error("Message is not visible")

    /** 以锚点时间读取有限上下文，禁止把整段历史载入内存。 */
    suspend fun messageWindow(
        conversation: ConversationRef,
        id: Long,
        context: Int
    ): List<AgentMessage> {
        require(context in 0..5) { "Invalid message context" }
        val anchor = message(conversation, id)
        if (context == 0) return listOf(anchor)
        val ids = when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatMessageDao()
                .agentWindow(conversation.id, id, context)

            is ConversationRef.Group -> mDatabase.getGroupChatMessageDao()
                .agentWindow(conversation.id, id, context)
        }
        return ids.mapNotNull { messageId ->
            runCatching {
                message(
                    conversation,
                    messageId
                )
            }.getOrNull()
        }
    }

    /** SQL 先过滤会话、人设和当前角色，不能通过物理主键读取其他成员私有记忆。 */
    suspend fun memories(context: ToolExecutionContext): List<MemoryEntry> =
        mMemories.visibleToAgent(target(context), context.characterId, context.personaId)

    /** 在已授权记忆之上叠加当前运行提案，预览修订不会被误报为永久保存。 */
    suspend fun memoryView(context: ToolExecutionContext): List<JsonObject> {
        val view = memories(context).map { entry ->
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
        proposals(context.token.runId).forEach { proposal ->
            val operation = proposal["operation"].asString
            if (operation !in setOf(
                    "upsert_memory",
                    "correct_memory",
                    "invalidate_memory"
                )
            ) return@forEach
            val old = view.firstOrNull {
                it["memoryId"] == proposal["memoryId"] && it["table"] == proposal["table"] && proposal.has(
                    "memoryId"
                )
            }
            val next = old?.deepCopy() ?: jsonObject(
                "kind" to proposal["kind"].asString,
                "status" to "Active"
            )
            proposal["body"]?.let { next.add("body", it.deepCopy()) }
            proposal["kind"]?.let { next.add("kind", it.deepCopy()) }
            next.addProperty(
                "status",
                if (operation == "invalidate_memory") "Invalidated" else "Active"
            )
            next.addProperty("revision", (proposal["expectedRevision"]?.asLong ?: 0) + 1)
            next.addProperty("proposed", true)
            proposal["proposalId"]?.let { next.add("proposalId", it.deepCopy()) }
            if (old != null) view.remove(old)
            view.add(next)
        }
        return view
    }

    /** 查询已完成调用里的未提交提案，检查点缺失必须停止，不能重算副作用。 */
    suspend fun proposals(runId: String): List<JsonObject> =
        mDatabase.getAgentRunDao().invocations(runId)
            .filter { it.writeDisposition == ToolWriteDisposition.Proposed.name }
            .map { invocation ->
                val payload = mCheckpoints.read(
                    requireNotNull(invocation.checkpointKey),
                    requireNotNull(invocation.resultDigest)
                ) ?: error("Proposal checkpoint missing")
                payload.getAsJsonObject("result").getAsJsonObject("data")
                    .getAsJsonObject("proposal").deepCopy()
            }.distinctBy { it["proposalId"]?.asString ?: AgentJson.canonical(it) }

    /** 场景读取叠加本次尚未提交的增量，并明确标记预览状态。 */
    suspend fun scene(context: ToolExecutionContext): JsonObject {
        val state = mScenes.get(context.token.conversation)
        val proposals =
            proposals(context.token.runId).filter { it["operation"].asString == "update_scene_state" }
        var json = state.stateJson
        proposals.forEach { json = SceneStateCodec.apply(json, changes(it)) }
        return jsonObject(
            "revision" to state.revision + proposals.size,
            "state" to AgentJson.parse(json),
            "proposed" to proposals.isNotEmpty()
        )
    }

    /** 将当前用户证据绑定为已提交事实，角色生成证据留待最终正文核验。 */
    suspend fun validateEvidence(context: ToolExecutionContext, proposal: JsonObject): Boolean {
        val id = proposal["sourceMessageId"]?.takeUnless { it.isJsonNull }?.asLong ?: 0L
        val quote = proposal["evidence"].asString
        if (quote.isBlank() || quote.length > 1000) {
            throw ToolPreparationException("evidence_empty_or_too_long")
        }
        // 优先核验模型指定的消息 ID；当模型未见到真实数据库主键时，仅在当前会话已提交用户消息内按原文精确查找。
        if (id > 0L) {
            val direct = runCatching { message(context.token.conversation, id) }.getOrNull()
            if (direct != null && direct.user) {
                val matched = matchEvidenceQuote(direct.content, quote)
                if (matched != null) {
                    bindCommittedEvidence(proposal, direct, matched)
                    return true
                }
            }
            val resolved = findCommittedUserMessage(context.token.conversation, quote)
                ?: throw ToolPreparationException("evidence_not_found_in_user_messages")
            bindCommittedEvidence(proposal, resolved.first, resolved.second)
            return true
        }
        // 省略或传 0 时，若变更不依赖本轮未提交提案且原文确属当前会话用户消息，则直接绑定为已提交证据。
        if (!dependsOnPendingProposal(context, proposal)) {
            val resolved = findCommittedUserMessage(context.token.conversation, quote)
            if (resolved != null) {
                bindCommittedEvidence(proposal, resolved.first, resolved.second)
                return true
            }
        }
        proposal.addProperty("sourceMessageId", 0L)
        return false
    }

    /** 将已核验的用户消息主键、逐字摘录与内容指纹写入提案，供后续短事务复用。 */
    private fun bindCommittedEvidence(
        proposal: JsonObject,
        source: AgentMessage,
        matchedQuote: String,
    ) {
        proposal.addProperty("sourceMessageId", source.id)
        proposal.addProperty("evidence", matchedQuote)
        proposal.addProperty(
            "sourceFingerprint",
            MemoryRestoreValidator.fingerprint(source.content)
        )
    }

    /** 仅在当前会话最近已提交的用户消息中查找包含证据原文的记录，不跨会话读取。 */
    private suspend fun findCommittedUserMessage(
        conversation: ConversationRef,
        quote: String,
    ): Pair<AgentMessage, String>? {
        // 仅读取当前会话最近有限条已提交用户消息，按最新优先匹配。
        val recentUserMessages = when (conversation) {
            is ConversationRef.Single -> mDatabase.getChatMessageDao()
                .getLatestMessagePageBySessionId(conversation.id, RECENT_EVIDENCE_SCAN_LIMIT)
                .filter { it.source.name == "User" && it.content.isNotBlank() }
                .map { AgentMessage(it.id, true, it.content) }

            is ConversationRef.Group -> mDatabase.getGroupChatMessageDao()
                .getLatestMessagePage(conversation.id, RECENT_EVIDENCE_SCAN_LIMIT)
                .filter { it.source.name == "User" && it.content.isNotBlank() }
                .map { AgentMessage(it.id, true, it.content) }
        }
        // 优先原串命中，再依次尝试去空白与去外层引号的候选子串。
        for (candidate in evidenceQuoteCandidates(quote)) {
            val matched = recentUserMessages.firstOrNull { it.content.contains(candidate) }
            if (matched != null) return matched to candidate
        }
        return null
    }

    /** 判断本条写入是否基于同轮未提交提案的预览版本号，避免抢先以已提交身份越过前序提案。 */
    private suspend fun dependsOnPendingProposal(
        context: ToolExecutionContext,
        proposal: JsonObject,
    ): Boolean {
        val operation =
            proposal["operation"]?.takeUnless { it.isJsonNull }?.asString ?: return false
        val expected =
            proposal["expectedRevision"]?.takeUnless { it.isJsonNull }?.asLong ?: return false
        if (operation == "update_scene_state") {
            return mScenes.get(context.token.conversation).revision != expected
        }
        val memoryId = proposal["memoryId"]?.takeUnless { it.isJsonNull }?.asLong ?: return false
        val table = proposal["table"]?.takeUnless { it.isJsonNull }?.asString ?: return false
        val existing =
            memories(context).firstOrNull { it.ref.id == memoryId && it.ref.table.name == table }
                ?: return false
        return existing.fields.currentRevision != expected
    }

    /** 短事务内提交已验证证据及版本的单个提案；网络与检查点读取必须已完成。 */
    suspend fun commitProposal(
        context: ToolExecutionContext,
        proposal: JsonObject,
        finalMessageId: Long
    ): Boolean {
        val proposed = proposal["sourceMessageId"]?.asLong == 0L
        val sourceId = proposal["sourceMessageId"]?.asLong?.takeIf { it > 0 } ?: finalMessageId
        val source = message(context.token.conversation, sourceId)
        val quote = proposal["evidence"].asString
        if (!source.content.contains(quote)) return false
        proposal["sourceFingerprint"]?.let {
            require(
                it.asString == MemoryRestoreValidator.fingerprint(
                    source.content
                )
            ) { "Evidence changed" }
        }
        val operation = proposal["operation"].asString
        if (operation == "update_scene_state") {
            // 前序提案未被最终正文支持时，不将依赖它的后续版本强行套到旧状态上。
            if (proposed && mScenes.get(context.token.conversation).revision != proposal["expectedRevision"].asLong) return false
            mScenes.applyAgentProposal(
                context.token.conversation,
                proposal["expectedRevision"].asLong,
                changes(proposal),
                sourceId
            )
            return true
        }
        // 引用只能来自当前可见集；写入继承条目必须显式提升授权，不能跨表偷偷改通用记忆。
        val existing = proposal["memoryId"]?.asLong?.takeIf { it > 0 }?.let { id ->
            memories(context).firstOrNull { it.ref.id == id && it.ref.table.name == proposal["table"]?.asString }
                ?: error("Memory is not visible")
        }
        if (operation == "promote_memory_scope") {
            requireNotNull(existing)
            mMemories.promote(
                existing.target,
                existing.ref,
                proposal["expectedRevision"].asLong,
                context.characterId
            )
            return true
        }
        require(existing == null || existing.target == target(context)) { "Inherited memory is read only" }
        if (proposed && existing != null && existing.fields.currentRevision != proposal["expectedRevision"].asLong) return false
        val body =
            if (operation == "invalidate_memory") requireNotNull(existing).fields.content.body else proposal["body"].asString
        val draft = MemoryDraft(
            body = body,
            kind = proposal["kind"]?.asString?.let(MemoryKind::valueOf)
                ?: existing?.fields?.content?.kind?.let(MemoryKind::valueOf) ?: MemoryKind.fact,
            status = if (operation == "invalidate_memory") MemoryStatus.Invalidated else MemoryStatus.Active,
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
            target(context),
            draft,
            existing?.ref,
            proposal["expectedRevision"]?.asLong ?: 0,
            listOf(evidence)
        )
        return true
    }

    /** 解析类型化路径及 JSON 字面量，再交给领域 Codec 验证半结构化约束。 */
    fun changes(proposal: JsonObject): List<SceneStateChange> =
        proposal.getAsJsonArray("changes").map { element ->
            val change = element.asJsonObject
            SceneStateChange(
                change.getAsJsonArray("path").map { it.asString },
                change["value_json"]?.takeUnless { it.isJsonNull }?.asString?.let {
                    AgentJson.parse(it)
                })
        }

    private fun target(context: ToolExecutionContext): MemoryTarget =
        when (val conversation = context.token.conversation) {
            is ConversationRef.Single -> MemoryTarget.Chat(conversation.id)
            is ConversationRef.Group -> MemoryTarget.Group(conversation.id)
        }

    companion object {
        private const val RECENT_EVIDENCE_SCAN_LIMIT = 50
        private val SURROUNDING_QUOTE_PAIRS = listOf(
            '"' to '"',
            '\'' to '\'',
            '“' to '”',
            '‘' to '’',
            '「' to '」',
            '『' to '』',
        )

        /**
         * 核验证据摘录是否为目标原文的逐字子串，仅容忍首尾空白或单层包裹引号，绝不接受改写。
         *
         * @param content 已提交消息或已接受回复的原文
         * @param quote 模型提供的证据摘录
         * @return 命中的逐字原文子串；未命中时返回 null
         */
        internal fun matchEvidenceQuote(content: String, quote: String): String? {
            if (content.isBlank() || quote.isBlank()) return null
            return evidenceQuoteCandidates(quote).firstOrNull { candidate ->
                content.contains(candidate)
            }
        }

        private fun evidenceQuoteCandidates(quote: String): List<String> {
            val candidates = linkedSetOf<String>()

            // 优先保持原串匹配，再尝试规范换行、去首尾空白及剥离单层外引号。
            fun addIfValid(value: String) {
                if (value.isNotBlank()) candidates.add(value)
            }
            addIfValid(quote)
            val normalizedNewlines = quote.replace("\r\n", "\n")
            addIfValid(normalizedNewlines)
            val trimmed = normalizedNewlines.trim()
            addIfValid(trimmed)
            for ((open, close) in SURROUNDING_QUOTE_PAIRS) {
                if (trimmed.length >= 2 && trimmed.first() == open && trimmed.last() == close) {
                    addIfValid(trimmed.substring(1, trimmed.length - 1).trim())
                }
            }
            return candidates.toList()
        }
    }
}

/** 证据校验使用原文，不将处理后的展示正则文本当作已提交事实。 */
data class AgentMessage(val id: Long, val user: Boolean, val content: String)
