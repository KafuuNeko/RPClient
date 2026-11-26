package me.kafuuneko.rpclient.libs.agent.tool.builtin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.CancellationException
import me.kafuuneko.rpclient.libs.agent.model.AgentJson
import me.kafuuneko.rpclient.libs.agent.model.ConversationRef
import me.kafuuneko.rpclient.libs.agent.model.SceneStateCodec
import me.kafuuneko.rpclient.libs.agent.tool.AgentTool
import me.kafuuneko.rpclient.libs.agent.tool.AgentToolDefinition
import me.kafuuneko.rpclient.libs.agent.tool.ToolAction
import me.kafuuneko.rpclient.libs.agent.tool.ToolEffect
import me.kafuuneko.rpclient.libs.agent.tool.ToolExecutionContext
import me.kafuuneko.rpclient.libs.agent.tool.ToolPreparationException
import me.kafuuneko.rpclient.libs.agent.tool.ToolResult
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.agent.tool.ToolUserRequestKind
import me.kafuuneko.rpclient.libs.agent.tool.ToolWriteDisposition
import me.kafuuneko.rpclient.libs.memory.MemoryKind
import me.kafuuneko.rpclient.libs.room.repository.AgentLocalDataRepository
import me.kafuuneko.rpclient.libs.room.repository.AgentRunRepository
import me.kafuuneko.rpclient.utils.jsonArray
import me.kafuuneko.rpclient.utils.jsonObject

/**
 * 内置工具集合注册入口，处理器与协议、页面和循环状态机解耦。
 * - 六组工具共享严格 Schema、分页及当前执行上下文。
 * - 领域异常只返回稳定错误码，不把 SQL、路径或异常原文交给模型。
 */
class BuiltinAgentTools(
    private val mData: AgentLocalDataRepository,
    private val mRuns: AgentRunRepository
) {
    /** 返回全部固定函数名及处理器，新增工具不需要修改运行循环。 */
    fun create(): List<AgentTool> =
        loreTools() + chatTools() + memoryTools() + mathTools() + sceneTools() + askTool()

    private class LocalTool(
        override val definition: AgentToolDefinition,
        private val handler: suspend (ToolExecutionContext, JsonObject) -> ToolAction,
    ) : AgentTool {
        /** 准备业务动作，实际派发与幂等发布仍由统一执行器负责。 */
        override suspend fun prepare(
            context: ToolExecutionContext,
            arguments: JsonObject
        ): ToolAction = handler(context, arguments)
    }

    private fun tool(
        name: String,
        description: String,
        parameters: ToolSchema,
        effect: ToolEffect = ToolEffect.ReadOnly,
        memory: Boolean = false,
        handler: suspend (ToolExecutionContext, JsonObject) -> ToolAction
    ): AgentTool = LocalTool(
        AgentToolDefinition(
            name,
            name,
            1,
            description,
            parameters,
            effect,
            parallelSafe = effect == ToolEffect.ReadOnly,
            writesMemory = memory
        ), handler
    )

    private fun read(block: suspend () -> ToolResult): ToolAction = ToolAction.Read {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            ToolResult.RecoverableError("invalid_local_tool_request")
        } catch (_: IllegalStateException) {
            ToolResult.RecoverableError("local_data_unavailable_or_changed")
        }
    }

    private fun loreTools(): List<AgentTool> = listOf(
        tool(
            "search_lore",
            "Search enabled lore entries for this conversation. Returns IDs and short excerpts; use the cursor for the next page.",
            searchSchema()
        ) { context, args ->
            read {
                val query = args["query"].asString
                val entries = mData.lore(context).filter {
                    it.name.contains(query, true) || it.content.contains(
                        query,
                        true
                    ) || it.keywords.contains(query, true)
                }
                page(entries.map {
                    jsonObject(
                        "id" to it.id,
                        "title" to it.name.take(100),
                        "excerpt" to it.content.take(150)
                    )
                }, args, context)
            }
        },
        tool(
            "read_lore_entry",
            "Read an enabled lore entry by ID. Text pages preserve the original content; cursor is a character offset.",
            objectSchema("id" to integer(1), "cursor" to integer(0), required = setOf("id"))
        ) { context, args ->
            read {
                val entry = mData.lore(context).firstOrNull { it.id == args["id"].asLong }
                    ?: return@read ToolResult.RecoverableError("lore_not_visible")
                textPage(entry.content, args["cursor"]?.asInt ?: 0, "id", entry.id)
            }
        },
    )

    private fun chatTools(): List<AgentTool> = listOf(
        tool(
            "search_chat",
            "Search only committed text in the current conversation. Returned cursors must be passed unchanged.",
            objectSchema(
                "query" to string(1, 200),
                "cursor" to string(1, 2000),
                required = setOf("query")
            )
        ) { context, args ->
            read {
                mData.searchChat(
                    context,
                    args["query"].asString,
                    args["cursor"]?.asString?.let { AgentJson.parse(it).asJsonObject })
            }
        },
        tool(
            "read_messages",
            "Read a current-conversation message and an optional bounded neighbouring window. Text is paged; IDs from another conversation are rejected.",
            objectSchema(
                "message_id" to integer(1),
                "context" to integer(0, 5),
                "cursor" to integer(0),
                required = setOf("message_id")
            )
        ) { context, args ->
            read {
                val messages = mData.messageWindow(
                    context.token.conversation,
                    args["message_id"].asLong,
                    args["context"]?.asInt ?: 0
                )
                val text =
                    messages.joinToString("\n\n") { "[${it.id} ${if (it.user) "user" else "character"}]\n${it.content}" }
                textPage(text, args["cursor"]?.asInt ?: 0, "messageId", args["message_id"].asLong)
            }
        },
    )

    private fun memoryTools(): List<AgentTool> {
        // 模型读取叠加提案视图，错误归属及失效状态仍在数据边界校验。
        val reads = listOf(
            tool(
                "search_memory",
                "Search visible character, conversation and speaker-private memories. Proposed writes are marked and have no permanent ID yet.",
                searchSchema()
            ) { context, args ->
                read {
                    val query = args["query"].asString
                    val items = mData.memoryView(context).filter {
                        it["status"].asString == "Active" && it["body"].asString.contains(
                            query,
                            true
                        )
                    }.map { entry ->
                        entry.deepCopy()
                            .apply { addProperty("excerpt", remove("body").asString.take(180)) }
                    }
                    page(items, args, context)
                }
            },
            tool(
                "read_memory",
                "Read a visible memory by its table and ID; inherited memories cannot be edited directly.",
                objectSchema(
                    "memoryId" to integer(1),
                    "table" to tableSchema(),
                    "proposalId" to string(1, 128),
                    "cursor" to integer(0)
                )
            ) { context, args ->
                read {
                    val entry = mData.memoryView(context).firstOrNull {
                        if (args.has("proposalId")) it["proposalId"] == args["proposalId"]
                        else args.has("memoryId") && args.has("table") && it["memoryId"] == args["memoryId"] && it["table"] == args["table"]
                    } ?: return@read ToolResult.RecoverableError("memory_not_visible")
                    val page = textPage(
                        entry["body"].asString,
                        args["cursor"]?.asInt ?: 0,
                        "revision",
                        entry["revision"].asLong
                    )
                    page.copy(data = page.data.apply {
                        add(
                            "proposed",
                            entry["proposed"]
                        )
                        add("status", entry["status"])
                    })
                }
            },
        )
        val descriptions = mapOf(
            "upsert_memory" to "Create or update a conversation memory. Provide an exact verbatim evidence quote from a committed user message (omit sourceMessageId to auto-bind the matching recent user message, or pass its ID from search_chat/read_messages), or pass sourceMessageId=0 for a proposed fact verified against your final reply. Omit memoryId, table, and expectedRevision when creating a new memory; supply all three only when updating an existing Chat/Group memory.",
            "correct_memory" to "Correct a visible conversation memory with an exact evidence quote and expectedRevision. The prior revision and evidence remain available.",
            "invalidate_memory" to "Invalidate a conversation memory using evidence, preserving its history. Does not physically delete the memory.",
            "promote_memory_scope" to "Promote a visible conversation memory to this speaker's character memory. Requires explicit user confirmation unless policy permits non-event, non-relationship promotion.",
        )
        val writes = descriptions.map { (name, description) ->
            val isUpsert = name == "upsert_memory"
            val properties = linkedMapOf(
                "memoryId" to integer(1).copy(
                    description = if (isUpsert) {
                        "Existing memory ID to update. Omit when creating a new memory."
                    } else {
                        "Target conversation memory ID returned by search_memory or read_memory."
                    },
                ),
                "table" to tableSchema().copy(
                    description = if (isUpsert) {
                        "Existing memory table (Chat or Group; Character is read-only). Omit when creating a new memory."
                    } else {
                        "Target memory table (Chat or Group; Character is read-only)."
                    },
                ),
                "expectedRevision" to integer(0).copy(
                    description = if (isUpsert) {
                        "Current revision of the existing memory being updated. Omit when creating a new memory."
                    } else {
                        "Current revision of the target memory returned by search_memory or read_memory."
                    },
                ),
                "sourceMessageId" to integer(0).copy(
                    description = "Optional committed user message ID. Omit to automatically match a recent user message containing evidence, or pass 0 for a proposed fact verified against your upcoming final reply.",
                ),
                "evidence" to string(1, 1000).copy(
                    description = "Exact verbatim substring quoted from a committed user message in this conversation, or from the final reply you are about to write.",
                ),
            )
            if (name in setOf("upsert_memory", "correct_memory")) {
                properties["body"] = string(1, 4000).copy(
                    description = "Concise, durable memory content to store.",
                )
                properties["kind"] = string(
                    1,
                    20
                ).copy(
                    description = "Memory category.",
                    enumValues = MemoryKind.entries.map { JsonPrimitive(it.name) },
                )
            }
            // sourceMessageId 允许省略，由本地数据层优先按原文匹配当前会话已提交用户消息。
            val required =
                setOf("evidence") + if (isUpsert) setOf(
                    "body",
                    "kind"
                ) else setOf(
                    "memoryId",
                    "table",
                    "expectedRevision"
                ) + if (name == "correct_memory") setOf("body", "kind") else emptySet()
            tool(
                name,
                description,
                ToolSchema(ToolSchema.Type.Object, properties = properties, required = required),
                ToolEffect.LocalWrite,
                memory = true
            ) { context, args ->
                prepareWrite(context, args.deepCopy().apply { addProperty("operation", name) })
            }
        }
        return reads + writes
    }

    private fun mathTools(): List<AgentTool> = listOf(
        tool(
            "roll_dice",
            "Roll NdM dice with optional kh/kl and a signed modifier, e.g. 4d6kh3+2. At most 100 dice and 10000 sides. The saved result is replayed on retry.",
            objectSchema("notation" to string(1, 80), required = setOf("notation")),
            ToolEffect.NonDeterministicRead
        ) { _, args ->
            read {
                val result = DiceNotation.roll(args["notation"].asString)
                ToolResult.Success(
                    jsonObject(
                        "rolls" to JsonArray().apply { result.rolls.forEach(::add) },
                        "kept" to JsonArray().apply { result.kept.forEach(::add) },
                        "modifier" to result.modifier,
                        "total" to result.total
                    )
                )
            }
        },
        tool(
            "calculate",
            "Evaluate bounded arithmetic: + - * / % ^, parentheses, pi, e, sqrt, abs, sin, cos, tan, ln, log10, floor, ceil, round. No code or variable access.",
            objectSchema("expression" to string(1, 1024), required = setOf("expression"))
        ) { _, args ->
            read {
                ToolResult.Success(jsonObject("value" to RestrictedCalculator.calculate(args["expression"].asString)))
            }
        },
    )

    private fun sceneTools(): List<AgentTool> = listOf(
        tool(
            "get_scene_state",
            "Read current scene state plus this run's proposed changes, with an optimistic revision number.",
            objectSchema()
        ) { context, _ -> read { ToolResult.Success(mData.scene(context)) } },
        tool(
            "update_scene_state",
            "Apply bounded path changes to location, time, attributes, inventory, goals or custom groups. value_json is a JSON literal; omit it to delete. expectedRevision prevents lost updates. Quote committed user evidence (omit sourceMessageId to auto-match a recent user message) or propose against this final reply with sourceMessageId=0.",
            objectSchema(
                "expectedRevision" to integer(0).copy(
                    description = "Current scene revision returned by get_scene_state.",
                ),
                "sourceMessageId" to integer(0).copy(
                    description = "Optional committed user message ID. Omit to auto-match a recent user message containing evidence, or pass 0 for a proposed change verified against your final reply.",
                ),
                "evidence" to string(1, 1000).copy(
                    description = "Exact verbatim substring from a committed user message or your upcoming final reply.",
                ),
                "changes" to ToolSchema(
                    ToolSchema.Type.Array,
                    minItems = 1,
                    maxItems = 30,
                    items = objectSchema(
                        "path" to ToolSchema(
                            ToolSchema.Type.Array,
                            minItems = 1,
                            maxItems = 5,
                            items = string(1, 100)
                        ), "value_json" to string(1, 4000), required = setOf("path")
                    )
                ),
                required = setOf("expectedRevision", "evidence", "changes")
            ),
            ToolEffect.LocalWrite
        ) { context, args ->
            val proposal = args.deepCopy().apply { addProperty("operation", "update_scene_state") }
            prepareWrite(context, proposal)
        },
    )

    private fun askTool(): List<AgentTool> = listOf(
        tool(
            "ask_user",
            "Ask the user a question and suspend this run for up to 24 hours. An answer is input only and never grants write confirmation.",
            objectSchema(
                "question" to string(1, 2000),
                "options" to ToolSchema(
                    ToolSchema.Type.Array,
                    items = string(1, 200),
                    maxItems = 8
                ),
                required = setOf("question")
            ),
            effect = ToolEffect.NonDeterministicRead,

            ) { _, args ->
            ToolAction.AwaitingUser(
                ToolResult.AwaitingUser(
                    args["question"].asString,
                    args.getAsJsonArray("options")?.map { it.asString }.orEmpty()
                )
            )
        })

    private suspend fun prepareWrite(
        context: ToolExecutionContext,
        proposal: JsonObject
    ): ToolAction {
        try {
            val operation = proposal["operation"].asString
            // 新建记忆先归一化表名和 0 版本等常见冗余参数，再进入证据与去重校验。
            if (operation == "upsert_memory" && !hasMemoryId(proposal)) {
                normalizeNewMemoryIdentity(context, proposal)
            }
            val immediate = mData.validateEvidence(context, proposal)
            proposal.addProperty("proposalId", context.invocationId)
            if (operation == "upsert_memory" && !hasMemoryId(proposal)) {
                // 同轮已提出相同内容时直接复用原提案，避免重复创建两条记忆。
                val duplicate = mData.proposals(context.token.runId).firstOrNull {
                    it["operation"].asString == operation && it["body"] == proposal["body"] && it["kind"] == proposal["kind"] && !it.has(
                        "memoryId"
                    )
                }
                if (duplicate != null) return ToolAction.LocalWrite(
                    ToolResult.Success(
                        jsonObject("proposal" to duplicate),
                        ToolWriteDisposition.Proposed
                    )
                ) { }
                // 已存在完全同内容同分类的会话记忆时转为幂等更新，避免产生重复条目。
                mData.memories(context)
                    .firstOrNull { entry -> entry.ref.table.name != "Character" && entry.fields.content.body == proposal["body"].asString && entry.fields.content.kind == proposal["kind"].asString }
                    ?.let { existing ->
                        proposal.addProperty("memoryId", existing.ref.id)
                        proposal.addProperty("table", existing.ref.table.name)
                        proposal.addProperty("expectedRevision", existing.fields.currentRevision)
                    }
            }
            if (operation.endsWith("memory") || operation == "promote_memory_scope") validateMemoryWrite(
                context,
                proposal,
                immediate
            )
            // 准备阶段只验证，不修改数据。尚未发生的剧情保留为可回看的提案。
            if (operation == "update_scene_state") {
                val state = mData.scene(context)
                if (state["revision"].asLong != proposal["expectedRevision"].asLong) {
                    throw ToolPreparationException("scene_revision_conflict")
                }
                SceneStateCodec.apply(state["state"].toString(), mData.changes(proposal))
            }
            // 记忆范围提升默认要求用户显式确认；即便开启自动提升，事件与关系仍必须确认。
            val confirmation = if (operation == "promote_memory_scope") {
                val entry = mData.memories(context)
                    .firstOrNull { it.ref.id == proposal["memoryId"].asLong && it.ref.table.name == proposal["table"].asString }
                    ?: throw ToolPreparationException("memory_not_visible")
                if (!mRuns.policy(context.token.conversation).autoPromoteMemory || entry.fields.content.kind in setOf(
                        "event",
                        "relationship"
                    )
                ) ToolResult.AwaitingUser(
                    "",
                    kind = ToolUserRequestKind.LocalWriteConfirmation,
                    details = jsonObject(
                        "operation" to operation,
                        "excerpt" to entry.fields.content.body.take(1000)
                    )
                ) else null
            } else null
            val result = ToolResult.Success(
                jsonObject("proposal" to proposal),
                if (immediate) ToolWriteDisposition.Committed else ToolWriteDisposition.Proposed
            )
            return ToolAction.LocalWrite(result, confirmation) {
                if (immediate) check(
                    mData.commitProposal(
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
        } catch (_: Exception) {
            throw ToolPreparationException("invalid_evidence_or_revision")
        }
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
            is ConversationRef.Single -> "Chat"
            is ConversationRef.Group -> "Group"
        }
        val table = proposal["table"]?.takeUnless { it.isJsonNull }?.asString
        if (table != null && table != expectedTable) {
            throw ToolPreparationException(
                if (table == "Character") "inherited_memory_read_only" else "invalid_memory_table"
            )
        }
        // 新建时仅允许省略或传 0 版本，非零版本缺少 memoryId 必须拒绝。
        val revision = proposal["expectedRevision"]?.takeUnless { it.isJsonNull }?.asLong
        if (revision != null && revision != 0L) {
            throw ToolPreparationException("incomplete_memory_identity")
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
                throw ToolPreparationException("incomplete_memory_identity")
            }
            return
        }
        if (!proposal.has("table") || !proposal.has("expectedRevision")) {
            throw ToolPreparationException("incomplete_memory_identity")
        }
        // 目标记忆必须存在于当前可见集，防止跨会话主键越权。
        val entry = mData.memories(context)
            .firstOrNull { it.ref.id == id && it.ref.table.name == proposal["table"].asString }
            ?: throw ToolPreparationException("memory_not_visible")
        val view = mData.memoryView(context)
            .first { it["memoryId"]?.asLong == id && it["table"]?.asString == entry.ref.table.name }
        if (view["status"].asString != "Active") {
            throw ToolPreparationException("memory_pending_invalidation")
        }
        if (immediate && view["revision"].asLong != entry.fields.currentRevision) {
            throw ToolPreparationException("memory_revision_conflict")
        }
        // 范围提升必须使用已提交证据且只能从会话表提升。
        if (proposal["operation"].asString == "promote_memory_scope") {
            if (entry.ref.table.name == "Character") {
                throw ToolPreparationException("inherited_memory_read_only")
            }
            if (!immediate) {
                throw ToolPreparationException("promotion_requires_committed_evidence")
            }
            if (entry.fields.currentRevision != proposal["expectedRevision"].asLong) {
                throw ToolPreparationException("memory_revision_conflict")
            }
            return
        }
        if (entry.ref.table.name == "Character") {
            throw ToolPreparationException("inherited_memory_read_only")
        }
        // 连续提案使用叠加视图的版本；已提交来源不能越过尚未发生的前序变更。
        val visible = view["revision"].asLong
        if (visible != proposal["expectedRevision"].asLong) {
            throw ToolPreparationException("memory_revision_conflict")
        }
    }

    private fun page(
        items: List<JsonObject>,
        args: JsonObject,
        context: ToolExecutionContext
    ): ToolResult.Success {
        // 只按完整条目分页，游标永远指向下一条而不是截断 JSON。
        val offset = args["offset"]?.asInt ?: 0
        val limit = args["limit"]?.asInt ?: 20
        require(offset in 0..items.size) { "Invalid page offset" }
        val selected = items.drop(offset).take(limit)
        val more = offset + selected.size < items.size
        return ToolResult.Success(
            jsonObject(
                "items" to jsonArray(selected),
                "historyVersion" to context.token.historyVersion
            ),
            truncated = more,
            nextCursor = if (more) (offset + selected.size).toString() else null
        )
    }

    private fun textPage(text: String, offset: Int, idName: String, id: Long): ToolResult.Success {
        require(offset in 0..text.length) { "Invalid text cursor" }
        val end = (offset + 6000).coerceAtMost(text.length)
        return ToolResult.Success(
            jsonObject(idName to id, "text" to text.substring(offset, end)),
            truncated = end < text.length,
            nextCursor = end.takeIf { it < text.length }?.toString()
        )
    }

    private fun searchSchema() = objectSchema(
        "query" to string(0, 200),
        "offset" to integer(0),
        "limit" to integer(1, 20),
        required = setOf("query")
    )

    private fun tableSchema() = string(1, 20).copy(
        enumValues = listOf(
            "Character",
            "Chat",
            "Group"
        ).map { JsonPrimitive(it) })

    private fun string(min: Int, max: Int) =
        ToolSchema(ToolSchema.Type.String, minLength = min, maxLength = max)

    private fun integer(min: Int, max: Int? = null) =
        ToolSchema(ToolSchema.Type.Integer, minimum = min.toDouble(), maximum = max?.toDouble())

    private fun objectSchema(
        vararg fields: Pair<String, ToolSchema>,
        required: Set<String> = emptySet()
    ) = ToolSchema(ToolSchema.Type.Object, properties = linkedMapOf(*fields), required = required)
}
