package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.macro.MacroKey
import me.kafuuneko.rpclient.libs.macro.MacroReference
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/** 已通过预算和角色归属展开的世界书插槽快照，同时提供正文与实际注入来源。 */
internal class GroupChatOutlets(entries: Map<String, List<LorebookEntry>>) {
    private val mEntries = entries.mapValues { (_, items) ->
        items.filter { it.content.isNotBlank() }.sortedByDescending { it.order }
    }
    private val mValues = mEntries.mapValues { (_, items) ->
        MacroValue.Literal(items.joinToString("\n") { it.content })
    }

    /** 世界书正文已完成自己的模板求值，插入其他模板时作为终值。 */
    fun value(reference: MacroReference): MacroValue? {
        if (reference.name != "outlet" || reference.argument == null) return null
        return mValues[reference.argument] ?: MacroValue.Literal("")
    }

    /** 仅为实际求值的插槽附加来源，供最终裁剪后的时序计算使用。 */
    fun sources(bindings: Set<MacroKey>): List<PromptSource> = bindings
        .filter { it.reference.name == "outlet" }
        .flatMap { mEntries[it.reference.argument].orEmpty() }
        .map { PromptSource(PromptSourceKind.WorldInfo, it.name, it.id) }
        .distinct()
}
