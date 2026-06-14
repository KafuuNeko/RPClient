package me.kafuuneko.rpclient.ui.message

sealed class MessageContentPart {
    data class Text(val content: String) : MessageContentPart()

    data class Think(
        val id: String,
        val content: String,
        val isComplete: Boolean = true
    ) : MessageContentPart()
}

fun String.toMessageContentParts(messageId: String): List<MessageContentPart> {
    val regex = Regex("<think>([\\s\\S]*?)(</think>|$)", RegexOption.IGNORE_CASE)
    val parts = mutableListOf<MessageContentPart>()
    var cursor = 0
    var foundThinkBlock = false
    regex.findAll(this).forEachIndexed { index, match ->
        foundThinkBlock = true
        if (match.range.first > cursor) {
            parts += MessageContentPart.Text(substring(cursor, match.range.first))
        }
        val thinkContent = match.groupValues[1].trim()
        if (thinkContent.isNotBlank() && !thinkContent.equals("null", ignoreCase = true)) {
            parts += MessageContentPart.Think(
                id = "$messageId:$index",
                content = thinkContent,
                isComplete = match.value.trimEnd().endsWith("</think>", ignoreCase = true)
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < length) {
        parts += MessageContentPart.Text(substring(cursor))
    }
    return when {
        parts.isNotEmpty() -> parts
        foundThinkBlock -> emptyList()
        else -> listOf(MessageContentPart.Text(this))
    }
}
