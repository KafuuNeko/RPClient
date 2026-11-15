package me.kafuuneko.rpclient.libs.prompt

import me.kafuuneko.rpclient.libs.llm.model.LLMMessageRole

/** 示例对话中一条已识别发言者角色的消息。 */
internal data class ParsedExampleMessage(
    val role: LLMMessageRole,
    val content: String
)

/**
 * 将角色卡示例块按“发言者: 内容”解析为 user/assistant 消息。
 *
 * 无发言者的新行会接到上一条消息，无法识别任何发言者时返回空列表，
 * 由调用方保留原始 system 文本作为兼容回退。
 */
internal fun parseExampleMessages(
    block: String,
    userName: String,
    characterName: String,
): List<ParsedExampleMessage> {
    val parsed = mutableListOf<ParsedExampleMessage>()

    block.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals(other = "<START>", ignoreCase = true) }
        .forEach { line ->
            val separator = line.indexOf(':')
            val label = if (separator >= 0) line.substring(startIndex = 0, endIndex = separator)
                .trim() else ""
            val role = when {
                // 先识别标记本身，即使用户与角色同名也能保留示例的发言身份。
                exampleUserLabel.matches(label) -> LLMMessageRole.User
                exampleCharacterLabel.matches(label) -> LLMMessageRole.Assistant
                label.equals(userName, ignoreCase = true) -> LLMMessageRole.User
                label.equals(characterName, ignoreCase = true) -> LLMMessageRole.Assistant
                else -> null
            }
            if (role != null) {
                parsed += ParsedExampleMessage(role, line.substring(separator + 1).trim())
            } else if (parsed.isNotEmpty()) {
                val previous = parsed.last()
                parsed[parsed.lastIndex] =
                    previous.copy(content = listOf(previous.content, line).joinToString("\n"))
            }
        }
    return parsed.filter { it.content.isNotBlank() }
}

private val exampleUserLabel = Regex("""\{\{\s*user\s*\}\}|<USER>""", RegexOption.IGNORE_CASE)
private val exampleCharacterLabel =
    Regex("""\{\{\s*char\s*\}\}|<(?:CHAR|BOT)>""", RegexOption.IGNORE_CASE)
