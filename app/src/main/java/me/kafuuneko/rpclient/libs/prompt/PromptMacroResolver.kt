package me.kafuuneko.rpclient.libs.prompt

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class PromptMacroResolver(
    private val mHistoryBuilder: FormattedHistoryBuilder
) {
    /**
     * 替换 prompt 中的基础 SillyTavern 宏。
     *
     * 未识别的宏保持原样，方便调试用户自定义 prompt 中尚未支持的语法。
     */
    fun resolve(
        template: String,
        context: PromptBuildContext,
        history: String = mHistoryBuilder.build(context.messages, context.userName, context.character.name),
        original: String = template,
        outlets: Map<String, String>? = null
    ): String {
        val firstMessages = context.character.getFirstMessageList()
        // 兼容旧式 <USER>/<BOT>/<CHAR> 写法，统一转换到 {{...}} 宏格式。
        var result = template
            .replace("<USER>", "{{user}}", ignoreCase = true)
            .replace("<BOT>", "{{char}}", ignoreCase = true)
            .replace("<CHAR>", "{{char}}", ignoreCase = true)

        result = result.replace(Regex("""\{\{\s*newline::(\d+)\s*\}\}""", RegexOption.IGNORE_CASE)) {
            "\n".repeat(it.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 1)
        }
        result = result.replace(Regex("""\{\{\s*space::(\d+)\s*\}\}""", RegexOption.IGNORE_CASE)) {
            " ".repeat(it.groupValues[1].toIntOrNull()?.coerceAtLeast(0) ?: 1)
        }
        result = result.replace(Regex("""\{\{\s*charFirstMessage::(\d+)\s*\}\}""", RegexOption.IGNORE_CASE)) {
            firstMessages.getOrNull(it.groupValues[1].toIntOrNull() ?: -1).orEmpty()
        }
        result = result.replace(Regex("""\{\{\s*outlet::([^}]+)\s*\}\}""", RegexOption.IGNORE_CASE)) {
            outlets?.get(it.groupValues[1].trim()) ?: if (outlets == null) it.value else ""
        }

        val now = LocalDateTime.now()
        val values = mapOf(
            "user" to context.userName,
            "char" to context.character.name,
            "group" to "",
            "charifnotgroup" to context.character.name,
            "description" to context.character.description,
            "personality" to context.character.personality,
            "scenario" to context.character.scenario,
            "persona" to context.session.userNote,
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
            "original" to original,
            "history" to history,
            "summary" to context.session.summarize,
            "lastmessage" to context.messages.lastOrNull()?.content.orEmpty(),
            "lastmessageid" to context.messages.lastOrNull()?.id?.toString().orEmpty(),
            "lastusermessage" to context.messages.lastOrNull { it.source.name.equals("User", ignoreCase = true) }?.content.orEmpty(),
            "lastcharmessage" to context.messages.lastOrNull { it.source.name.equals("Char", ignoreCase = true) }?.content.orEmpty(),
            "firstincludedmessageid" to context.messages.firstOrNull()?.id?.toString().orEmpty(),
            "time" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            "date" to LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            "weekday" to now.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()),
            "isotime" to now.format(DateTimeFormatter.ISO_DATE_TIME),
            "isodate" to now.format(DateTimeFormatter.ISO_DATE),
            "model" to context.provider?.model.orEmpty(),
            "maxcontexttokens" to context.maxContextTokens.toString(),
            "maxresponsetokens" to context.maxResponseTokens.toString(),
            "maxprompt" to (context.maxContextTokens - context.maxResponseTokens).coerceAtLeast(0).toString(),
            "newline" to "\n",
            "space" to " ",
            "noop" to ""
        )

        return result.replace(Regex("""\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}""")) {
            values[it.groupValues[1].lowercase()] ?: it.value
        }
    }
}
