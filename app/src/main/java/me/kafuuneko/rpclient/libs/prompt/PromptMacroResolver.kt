package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.prompt.model.PromptBuildContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
     * - 装配并全局替换常用上下文宏（`{{user}}`、`{{char}}`、`{{description}}`、`{{personality}}`、`{{scenario}}`、`{{persona}}`、`{{history}}`、`{{summary}}`、`{{date}}`、`{{time}}` 等）。
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
    ): String {
        val firstMessages = context.character.getChatFirstMessageList()
        // 先展开跨聊天与故事共用的名称宏，避免不同 Prompt 构建器产生兼容差异。
        var result = resolveCharacterUserMacros(
            template = template,
            characterName = context.character.name,
            userName = context.userName,
        )

        // 解析带参数的格式化宏与动态开场白宏
        result = result.replace(
            Regex(
                """\{\{\s*newline::(\d+)\s*\}\}""",
                RegexOption.IGNORE_CASE
            )
        ) { match ->
            "\n".repeat(match.groupValues[1].toIntOrNull() ?: 1)
        }
        result = result.replace(
            Regex(
                """\{\{\s*space::(\d+)\s*\}\}""",
                RegexOption.IGNORE_CASE
            )
        ) { match ->
            " ".repeat(match.groupValues[1].toIntOrNull() ?: 1)
        }
        result = result.replace(
            Regex(
                """\{\{\s*charFirstMessage::(\d+)\s*\}\}""",
                RegexOption.IGNORE_CASE
            )
        ) { match ->
            firstMessages.getOrNull(match.groupValues[1].toIntOrNull() ?: -1).orEmpty()
        }
        // 解析自定义插槽宏
        result = result.replace(
            Regex(
                """\{\{\s*outlet::([^}]+)\s*\}\}""",
                RegexOption.IGNORE_CASE
            )
        ) { match ->
            outlets?.get(match.groupValues[1].trim()) ?: if (outlets == null) match.value else ""
        }

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
            "original" to original,
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
            "time" to LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
            "date" to LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
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

        // 正则替换所有已匹配宏，未识别宏保留原文
        return result.replace(Regex("""\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}""")) { match ->
            values[match.groupValues[1].lowercase()] ?: match.value
        }
    }
}

/**
 * 展开角色卡与 Prompt 模板中共用的角色名、用户名宏。
 *
 * 传入 null 的名称保持对应宏原文，供没有唯一当前角色的多角色故事上下文继续诊断；
 * 旧式 `<BOT>`、`<CHAR>`、`<USER>` 标签会先规范化，再按相同规则解析。
 */
fun resolveCharacterUserMacros(
    template: String,
    characterName: String?,
    userName: String?,
): String {
    val normalized = template
        .replace(oldValue = "<USER>", newValue = "{{user}}", ignoreCase = true)
        .replace(oldValue = "<BOT>", newValue = "{{char}}", ignoreCase = true)
        .replace(oldValue = "<CHAR>", newValue = "{{char}}", ignoreCase = true)
    return normalized.replace(
        regex = Regex("""\{\{\s*(char|user)\s*\}\}""", RegexOption.IGNORE_CASE),
    ) { match ->
        when (match.groupValues[1].lowercase()) {
            "char" -> characterName ?: match.value
            "user" -> userName ?: match.value
            else -> match.value
        }
    }
}
