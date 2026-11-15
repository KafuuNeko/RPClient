package me.kafuuneko.rpclient.libs.groupchat

import me.kafuuneko.rpclient.libs.prompt.model.PromptMessageDraft
import me.kafuuneko.rpclient.libs.prompt.model.PromptSource
import me.kafuuneko.rpclient.libs.prompt.model.PromptSourceKind
import me.kafuuneko.rpclient.libs.room.entity.LorebookEntry

/**
 * 展开本轮已通过世界书预算的命名插槽。
 * - 按条目顺序注入，并把实际注入的来源交给最终裁剪和时序计算。
 * - 用户草稿单独按模板区段处理，历史消息与世界书正文不递归解释插槽。
 */
internal class GroupChatOutlets(entries: Map<String, List<LorebookEntry>>) {
    private val mEntries = entries.mapValues { (_, items) -> items.sortedByDescending { it.order } }

    /** 解析指令模板中的插槽，将实际贡献正文的条目添加到 [sources]。 */
    fun expand(template: String, sources: MutableSet<PromptSource>): String =
        outlet.replace(template) { match ->
            mEntries[match.groupValues[1].trim()].orEmpty().filter { it.content.isNotBlank() }
                .joinToString("\n") { entry ->
                    sources += PromptSource(PromptSourceKind.WorldInfo, entry.name, entry.id)
                    entry.content
                }
        }

    /** 为支持插槽的提示词保留来源，空插槽不产生空消息。 */
    fun expandPrompt(draft: PromptMessageDraft): PromptMessageDraft? {
        if (draft.source.kind !in templateSources || !outlet.containsMatchIn(draft.content)) return draft
        val sources = draft.sources.toMutableSet()
        val text = expand(draft.content, sources)
        return if (text.isBlank()) null else draft.copy(content = text, sources = sources.toList())
    }

    private companion object {
        val outlet = Regex("""\{\{\s*outlet::([^}]+)\s*\}\}""", RegexOption.IGNORE_CASE)
        val templateSources = setOf(
            PromptSourceKind.MainPrompt,
            PromptSourceKind.AuxiliaryPrompt,
            PromptSourceKind.PostHistoryInstructions,
            PromptSourceKind.NewChatMarker,
            PromptSourceKind.UserNote,
            PromptSourceKind.GroupNudge,
            PromptSourceKind.ContinueNudge,
            PromptSourceKind.ImpersonationNudge,
            PromptSourceKind.SummaryTask
        )
    }
}
