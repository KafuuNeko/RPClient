package me.kafuuneko.rpclient.libs.prompt.macro

import me.kafuuneko.rpclient.libs.macro.MacroExpander
import me.kafuuneko.rpclient.libs.macro.MacroExpansionException
import me.kafuuneko.rpclient.libs.macro.MacroScope
import me.kafuuneko.rpclient.libs.macro.MacroValue
import me.kafuuneko.rpclient.libs.prompt.FormattedHistoryBuilder
import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * SillyTavern 风格 Prompt 宏解析器。
 *
 * 未实现或未知宏有意保留原文，便于兼容第三方模板并在调试检查器中发现缺失能力。
 */
class PromptMacroResolver(
    private val mHistoryBuilder: FormattedHistoryBuilder
) {
    /**
     * 替换 prompt 中的基础 SillyTavern 宏标签。
     *
     * 处理步骤：
     * - 兼容旧式 `<USER>` / `<BOT>` / `<CHAR>` 标签并规范化；
     * - 解析带参数宏：`{{newline::N}}`、`{{space::N}}`、`{{charFirstMessage::N}}` 与 `{{outlet::NAME}}`；
     * - 按需展开常用上下文宏（`{{user}}`、`{{char}}`、`{{description}}`、`{{personality}}`、`{{scenario}}`、`{{persona}}`、`{{history}}`、`{{summary}}`、`{{date}}`、`{{time}}` 等）。
     */
    fun resolve(
        template: String,
        context: PromptBuildContext,
        history: String = mHistoryBuilder.build(
            context.messages,
            context.userName,
            context.character.name
        ),
        original: String = template,
        outlets: Map<String, String>? = null,
        bindings: Map<String, MacroValue> = emptyMap(),
    ): String {
        val scope = scope(context, history, outlets, bindings).withValues(
            mapOf("original" to MacroValue.Template(original))
        )
        return promptMacroExpander.expand(template, scope).text
    }

    /**
     * 从会话快照创建宏作用域，模板字段和普通正文具有明确的值语义。
     * @param context 会话、角色与身份快照。
     * @param history 已准备的历史正文。
     * @param outlets 已通过世界书预算的插槽正文。
     * @param additionalBindings 当前任务额外提供的绑定，例如摘要字数。
     * @return 可在本次构建各片段间复用的只读作用域。
     */
    internal fun scope(
        context: PromptBuildContext,
        history: String,
        outlets: Map<String, String>?,
        additionalBindings: Map<String, MacroValue> = emptyMap(),
    ): MacroScope {
        val firstMessages = context.character.getChatFirstMessageList()
        // 装配基础环境与上下文变量字典
        val now = LocalDateTime.now()
        val values = mapOf(
            "user" to context.userName,
            "char" to context.character.name,
            "group" to "",
            "charifnotgroup" to context.character.name,
            "description" to context.character.description,
            "personality" to context.character.personality,
            "scenario" to context.character.scenario,
            "persona" to context.userDescription,
            "charcreatornotes" to context.character.creatorNotes,
            "creator" to context.character.creator,
            "charversion" to context.character.characterVersion,
            "character_version" to context.character.characterVersion,
            "characternote" to context.character.depthPromptPrompt,
            "depthprompt" to context.character.depthPromptPrompt,
            "mesexamples" to context.character.examplesOfDialogue,
            "mesexamplesraw" to context.character.examplesOfDialogue,
            "charfirstmessage" to firstMessages.firstOrNull().orEmpty(),
            "charinstruction" to context.character.postHistoryInstructions,
            "history" to history,
            "summary" to context.summary,
            "lastmessage" to context.messages.lastOrNull()?.content.orEmpty(),
            "lastmessageid" to context.messages.lastOrNull()?.id?.toString().orEmpty(),
            "lastusermessage" to context.messages.lastOrNull {
                it.source.name.equals(
                    other = "User",
                    ignoreCase = true
                )
            }?.content.orEmpty(),
            "lastcharmessage" to context.messages.lastOrNull {
                it.source.name.equals(
                    other = "Char",
                    ignoreCase = true
                )
            }?.content.orEmpty(),
            "firstincludedmessageid" to context.messages.firstOrNull()?.id?.toString().orEmpty(),
            "time" to now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
            "date" to now.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
            "weekday" to now.dayOfWeek.getDisplayName(
                java.time.format.TextStyle.FULL,
                Locale.getDefault()
            ),
            "isotime" to now.format(DateTimeFormatter.ISO_DATE_TIME),
            "isodate" to now.format(DateTimeFormatter.ISO_DATE),
            "model" to context.provider?.model.orEmpty(),
            "maxcontexttokens" to context.maxContextTokens.toString(),
            "maxresponsetokens" to context.maxResponseTokens.toString(),
            "maxprompt" to (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(0)
                .toString(),
            "newline" to "\n",
            "space" to " ",
            "noop" to "",
        )

        // 可组合的人物字段继续解释引用；历史、摘要、姓名等数据均作为终值。
        val templates = setOf(
            "description", "personality", "scenario", "persona", "charcreatornotes",
            "characternote", "depthprompt", "mesexamples", "mesexamplesraw",
            "charfirstmessage", "charinstruction",
        )
        val bindings = values.mapValues { (name, value) ->
            if (name in templates) MacroValue.Template(value) else MacroValue.Literal(value)
        }
        return MacroScope(bindings + additionalBindings) { reference ->
            val argument = reference.argument ?: return@MacroScope null
            when (reference.name) {
                "newline", "space" -> {
                    if (argument.isEmpty() || argument.any { !it.isDigit() }) return@MacroScope null
                    val count = argument.toIntOrNull() ?: 1
                    if (count > MacroExpander.DEFAULT_MAX_OUTPUT_CHARS) {
                        throw MacroExpansionException.OutputLimit(MacroExpander.DEFAULT_MAX_OUTPUT_CHARS)
                    }
                    MacroValue.Literal((if (reference.name == "newline") "\n" else " ").repeat(count))
                }

                "charfirstmessage" -> {
                    if (argument.isEmpty() || argument.any { !it.isDigit() }) return@MacroScope null
                    MacroValue.Template(
                        firstMessages.getOrNull(argument.toIntOrNull() ?: -1).orEmpty()
                    )
                }

                "outlet" -> outlets?.let { MacroValue.Template(it[argument].orEmpty()) }
                else -> null
            }
        }
    }
}

/**
 * 展开角色卡与 Prompt 模板中共用的角色名、用户名宏。
 *
 * 传入 null 的名称保持对应宏原文，供没有唯一当前角色的多角色故事上下文继续诊断；
 * 旧式 `<BOT>`、`<CHAR>`、`<USER>` 标签由 Prompt 兼容语法映射到同一引用。
 */
fun resolveCharacterUserMacros(
    template: String,
    characterName: String?,
    userName: String?,
): String {
    val values = buildMap {
        characterName?.let { put("char", MacroValue.Literal(it)) }
        userName?.let { put("user", MacroValue.Literal(it)) }
    }
    return promptMacroExpander.expand(template, MacroScope(values)).text
}
