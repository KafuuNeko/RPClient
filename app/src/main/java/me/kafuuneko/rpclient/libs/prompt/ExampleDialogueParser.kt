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
    characterName: String
): List<ParsedExampleMessage> {
    val userLabels = setOf(userName, "{{user}}", "<USER>")
    val characterLabels = setOf(characterName, "{{char}}", "<CHAR>", "<BOT>")
    val parsed = mutableListOf<ParsedExampleMessage>()

    block.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.equals("<START>", ignoreCase = true) }
        .forEach { line ->
            val separator = line.indexOf(':')
            val label = if (separator >= 0) line.substring(0, separator).trim() else ""
            val role = when {
                userLabels.any { it.equals(label, ignoreCase = true) } -> LLMMessageRole.User
                characterLabels.any { it.equals(label, ignoreCase = true) } ->
                    LLMMessageRole.Assistant
                else -> null
            }
            if (role != null) {
                parsed += ParsedExampleMessage(role, line.substring(separator + 1).trim())
            } else if (parsed.isNotEmpty()) {
                val previous = parsed.last()
                parsed[parsed.lastIndex] = previous.copy(
                    content = listOf(previous.content, line).joinToString("\n")
                )
            }
        }
    return parsed.filter { it.content.isNotBlank() }
}
