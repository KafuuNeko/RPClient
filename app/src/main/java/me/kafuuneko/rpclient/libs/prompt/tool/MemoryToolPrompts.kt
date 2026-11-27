package me.kafuuneko.rpclient.libs.prompt.tool

import com.google.gson.JsonPrimitive
import me.kafuuneko.rpclient.libs.agent.tool.ToolSchema
import me.kafuuneko.rpclient.libs.memory.MemoryKind

/** 记忆工具的模型说明与参数契约，文本不参与宏展开。 */
object MemoryToolPrompts {
    val SearchMemory = ToolPromptSpec(
        description = "Search visible character, conversation and speaker-private memories. Proposed writes are marked and have no permanent ID yet.",
        parameters = searchSchema(),
    )

    val ReadMemory = ToolPromptSpec(
        description = "Read a visible memory by its table and ID; inherited memories cannot be edited directly.",
        parameters = objectSchema(
            "memoryId" to integer(1),
            "table" to tableSchema(),
            "proposalId" to string(1, 128),
            "cursor" to integer(0),
        ),
    )

    val UpsertMemory = ToolPromptSpec(
        description = "Create or update a conversation memory. Provide an exact verbatim evidence quote from a committed user message (omit sourceMessageId to auto-bind the matching recent user message, or pass its ID from search_chat/read_messages), or pass sourceMessageId=0 for a proposed fact verified against your final reply. Omit memoryId, table, and expectedRevision when creating a new memory; supply all three only when updating an existing Chat/Group memory.",
        parameters = memoryWriteSchema("upsert_memory"),
    )

    val CorrectMemory = ToolPromptSpec(
        description = "Correct a visible conversation memory with an exact evidence quote and expectedRevision. The prior revision and evidence remain available.",
        parameters = memoryWriteSchema("correct_memory"),
    )

    val InvalidateMemory = ToolPromptSpec(
        description = "Invalidate a conversation memory using evidence, preserving its history. Does not physically delete the memory.",
        parameters = memoryWriteSchema("invalidate_memory"),
    )

    val PromoteMemoryScope = ToolPromptSpec(
        description = "Promote a visible conversation memory to this speaker's character memory. Requires explicit user confirmation unless policy permits non-event, non-relationship promotion.",
        parameters = memoryWriteSchema("promote_memory_scope"),
    )

    private fun memoryWriteSchema(name: String): ToolSchema {
        // 先组装所有记忆写工具共用的身份定位、乐观版本与逐字证据参数。
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
        // 仅新增与更正操作需要写入正文和分类，失效与范围提升只引用原记录。
        if (name in setOf("upsert_memory", "correct_memory")) {
            properties["body"] = string(1, 4000).copy(
                description = "Concise, durable memory content to store.",
            )
            properties["kind"] = string(1, 20).copy(
                description = "Memory category.",
                enumValues = MemoryKind.entries.map { JsonPrimitive(it.name) },
            )
        }
        val required = setOf("evidence") + if (isUpsert) {
            setOf("body", "kind")
        } else {
            setOf("memoryId", "table", "expectedRevision") +
                if (name == "correct_memory") setOf("body", "kind") else emptySet()
        }
        return ToolSchema(ToolSchema.Type.Object, properties = properties, required = required)
    }

}
