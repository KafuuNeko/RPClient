package me.kafuuneko.rpclient.libs.prompt.summary

import me.kafuuneko.rpclient.libs.llm.model.LLMContentBlock
import me.kafuuneko.rpclient.libs.llm.model.LLMImageReference
import me.kafuuneko.rpclient.libs.llm.model.LLMMessage
import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole
import me.kafuuneko.rpclient.libs.llm.model.messageWithBlocks
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.macro.promptMacroExpander
import me.kafuuneko.rpclient.libs.prompt.preset.PromptTemplateSnapshot
import me.kafuuneko.rpclient.libs.prompt.preset.PromptType

private val rawSummaryMacroPattern = Regex("""\{\{([^{}]*)\}\}""")

/** 为预设编辑器和请求构建提供同一条 Raw 素材占位符规则。 */
internal fun PromptType.requiredRawSummaryMacro(): String? = when (this) {
    PromptType.RawSummaryPreviousFormat -> "summary"
    PromptType.RawSummaryHistoryFormat -> "history"
    PromptType.RawSummaryMessageFormat -> "message"
    else -> null
}

/** 读取唯一的素材占位符前后文本，避免模板漏掉或重复发送历史。 */
internal fun PromptType.splitRawSummaryFormat(template: String): Pair<String, String>? {
    val macro = requiredRawSummaryMacro() ?: return null
    val match = rawSummaryMacroPattern.findAll(template).singleOrNull { reference ->
        reference.groupValues[1].trim().equals(macro, ignoreCase = true)
    } ?: return null
    return template.substring(0, match.range.first) to template.substring(match.range.last + 1)
}

/** Raw 摘要中的单条历史消息，渲染前保留署名与其图文的对应关系。 */
internal data class RawSummaryHistoryMessage(
    val speakerName: String,
    val content: String,
    val images: List<LLMImageReference> = emptyList(),
)

/** 使用同一预设快照组装摘要任务和按预算选中的图文素材。 */
internal fun buildRawSummaryMessages(
    templates: PromptTemplateSnapshot,
    instruction: String,
    existingSummary: String,
    history: List<RawSummaryHistoryMessage>,
): List<LLMMessage> {
    // 只有最终位于本轮素材中的图片才需要视觉摘要指令。
    val imageInstruction = if (history.any { message -> message.images.isNotEmpty() }) {
        templates.rawSummaryImagePrompt.trim()
    } else ""
    val systemPrompt = listOf(instruction.trim(), imageInstruction)
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    // 旧摘要是字面文本，历史占位符承载有序图文块。
    val material = buildList {
        if (existingSummary.isNotBlank()) {
            val (prefix, suffix) = templates.rawSummaryFormat(PromptType.RawSummaryPreviousFormat)
            add(LLMContentBlock.Text("$prefix$existingSummary$suffix"))
        }
        if (history.isNotEmpty()) {
            addAll(wrapRawSummaryHistory(history, templates))
        }
    }
    return listOf(
        LLMMessage(role = LLMMessageRole.System, content = systemPrompt),
        messageWithBlocks(role = LLMMessageRole.User, blocks = material),
    ).filter { it.content.isNotBlank() || it.images.isNotEmpty() }
}

/** 按单条消息应用格式，再用历史模板包裹完整的有序图文。 */
private fun wrapRawSummaryHistory(
    history: List<RawSummaryHistoryMessage>,
    templates: PromptTemplateSnapshot,
): List<LLMContentBlock> = buildList {
    val (historyPrefix, historySuffix) = templates.rawSummaryFormat(PromptType.RawSummaryHistoryFormat)
    val (messagePrefix, messageSuffix) = templates.rawSummaryFormat(PromptType.RawSummaryMessageFormat)
    val text = StringBuilder(historyPrefix)
    // 只有模板中的发言者占位符参与展开，署名和消息正文都按字面值发送。
    history.forEachIndexed { index, message ->
        if (index > 0 && text.isNotEmpty()) text.append('\n')
        val scope = MacroScope(mapOf("speaker" to MacroValue.Literal(message.speakerName)))
        val prefix = promptMacroExpander.expand(messagePrefix, scope).text
        val suffix = promptMacroExpander.expand(messageSuffix, scope).text

        if (message.images.isEmpty()) {
            text.append(prefix).append(message.content)
        } else {
            // 图片前保留原有换行分隔，模板本身换行时避免重复叠加。
            text.append(prefix.trimEnd(' ', '\t'))
            if (text.isNotBlank()) {
                if (text.last() != '\n') text.append('\n')
                add(LLMContentBlock.Text(text.toString()))
            }
            text.clear()
            message.images.forEach { image -> add(LLMContentBlock.Image(image)) }
            if (message.content.isNotBlank()) text.append(message.content)
        }
        // 即使没有正文，单条消息的后缀也必须位于它的最后一张图片之后。
        text.append(suffix)
    }
    text.append(historySuffix)
    if (text.isNotBlank()) add(LLMContentBlock.Text(text.toString()))
}

/** 仓库外编辑的预设可能包含无效格式，发送时回退默认包装以保全素材。 */
private fun PromptTemplateSnapshot.rawSummaryFormat(type: PromptType): Pair<String, String> =
    type.splitRawSummaryFormat(values[type].orEmpty())
        ?: checkNotNull(type.splitRawSummaryFormat(type.defaultValue))
